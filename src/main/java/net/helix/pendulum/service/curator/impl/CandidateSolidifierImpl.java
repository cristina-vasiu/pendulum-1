package net.helix.pendulum.service.curator.impl;

import net.helix.pendulum.TransactionValidator;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.curator.CandidateSolidifier;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.utils.log.interval.IntervalLogger;
import net.helix.pendulum.utils.thread.DedicatedScheduledExecutorService;
import net.helix.pendulum.utils.thread.SilentScheduledExecutorService;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * This class implements the basic contract of the {@link CandidateSolidifier} interface.
 * </p>
 * <p>
 * It manages a map of unsolid candidates to collect all candidates that have to be solidified. It then periodically
 * issues checkSolidity calls on the earliest candidates to solidify them.
 * </p>
 * <p>
 * To save resources and make the call a little bit more efficient, we cache the earliest candidates in a separate map,
 * so the relatively expensive task of having to search for the next earliest candidates in the pool only has to be
 * performed after a candidates has become solid or irrelevant for our node.
 * </p>
 */
public class CandidateSolidifierImpl implements CandidateSolidifier {
    /**
     * Defines the amount of candidates that we "simultaneously" try to solidify in one pass.
     */
    private static final int SOLIDIFICATION_QUEUE_SIZE = 2;

    /**
     * Defines the interval in which solidity checks are issued (in milliseconds).
     */
    private static final int SOLIDIFICATION_INTERVAL = 5000;

    /**
     * <p>
     * Defines the maximum amount of transactions that are allowed to get processed while trying to solidify a
     * candidate.
     * </p>
     * <p>
     * Note: We want to find the next previous candidate and not get stuck somewhere at the end of the tangle with a
     *       long running {@link TransactionValidator#checkSolidity(Hash, boolean)} call.
     * </p>
     */
    private static final int SOLIDIFICATION_TRANSACTIONS_LIMIT = 50000;

    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private static final IntervalLogger log = new IntervalLogger(CandidateSolidifier.class);

    /**
     * Holds the snapshot provider which gives us access to the relevant snapshots.
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds a reference to the TransactionValidator which allows us to issue solidity checks.
     */
    private TransactionValidator transactionValidator;

    /**
     * Holds a reference to the manager of the background worker.
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Candidate Solidifier", log.delegate());

    /**
     * <p>
     * Holds the candidates that were newly added, but not examined yet.
     * </p>
     * <p>
     * Note: This is used to be able to add candidates to the solidifier without having to synchronize the access to the
     *       underlying Maps.
     * </p>
     */
    private final Map<Hash, Integer> newlyAddedCandidates = new ConcurrentHashMap<>();

    /**
     * Holds all unsolid candidates that shall be solidified (the transaction hash mapped to its candidate index).
     */
    private final Map<Hash, Integer> unsolidCandidatesPool = new ConcurrentHashMap<>();

    /**
     * Holds the candidates that are actively trying to be solidified by the background {@link Thread} (acts as a
     * Queue).
     */
    private final Map<Hash, Integer> candidatesToSolidify = new HashMap<>();

    /**
     * <p>
     * Holds and entry that represents the youngest candidate in the {@link #candidatesToSolidify} Map.
     * </p>
     * <p>
     * Note: It is used to check if new candidates that are being added, are older that the currently processed ones and
     *       should replace them in the queue (we solidify from oldest to youngest).
     * </p>
     */
    private Map.Entry<Hash, Integer> youngestCandidateInQueue = null;

    /**
     * <p>
     * This method initializes the instance and registers its dependencies.
     * </p>
     * <p>
     * It stores the passed in values in their corresponding private properties.
     * </p>
     * <p>
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:
     * </p>
     *       {@code candidateSolidifier = new CandidateSolidifierImpl().init(...);}
     *
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots
     * @param transactionValidator TransactionValidator instance that is used by the node
     * @return the initialized instance itself to allow chaining
     */
    public CandidateSolidifierImpl init(SnapshotProvider snapshotProvider, TransactionValidator transactionValidator) {
        this.snapshotProvider = snapshotProvider;
        this.transactionValidator = transactionValidator;

        return this;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Since this method might be called from a performance critical context, we simply add the candidate to a temporary
     * pool, that gets examined later by the background process. This doesn't just speed up the addition of new jobs but
     * also prevents us from having to synchronize the access to the underlying maps.
     * </p>
     */
    @Override
    public void add(Hash candidateHash, int roundIndex) {
        if (!unsolidCandidatesPool.containsKey(candidateHash) && !newlyAddedCandidates.containsKey(candidateHash) &&
                roundIndex > snapshotProvider.getInitialSnapshot().getIndex()) {

            newlyAddedCandidates.put(candidateHash, roundIndex);
        }
    }

    @Override
    public void start() {
        executorService.silentScheduleWithFixedDelay(this::candidateSolidificationThread, 0, SOLIDIFICATION_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }

    /**
     * <p>
     * This method takes an entry from the {@link #unsolidCandidatesPool} and adds it to the
     * {@link #candidatesToSolidify} queue.
     * </p>
     * <p>
     * It first checks if the given candidate is already part of the queue and then tries to add it. If the queue is not
     * full yet, the addition to the queue is relatively cheap, because the {@link #youngestCandidateInQueue} marker can
     * be updated without iterating over all entries. If the queue reached its capacity already, we replace the entry
     * marked by the {@link #youngestCandidateInQueue} marker and update the marker by recalculating it using
     * {@link #determineYoungestCandidateInQueue()}.
     * </p>
     *
     * @param candidateEntry entry from the {@link #unsolidCandidatesPool} that shall get added to the queue
     */
    private void addToSolidificationQueue(Map.Entry<Hash, Integer> candidateEntry) {
        if (candidatesToSolidify.containsKey(candidateEntry.getKey())) {
            return;
        }

        if (candidatesToSolidify.size() < SOLIDIFICATION_QUEUE_SIZE) {
            candidatesToSolidify.put(candidateEntry.getKey(), candidateEntry.getValue());

            if (youngestCandidateInQueue == null || candidateEntry.getValue() > youngestCandidateInQueue.getValue()) {
                youngestCandidateInQueue = candidateEntry;
            }
        } else if (candidateEntry.getValue() < youngestCandidateInQueue.getValue()) {
            candidatesToSolidify.remove(youngestCandidateInQueue.getKey());
            candidatesToSolidify.put(candidateEntry.getKey(), candidateEntry.getValue());

            determineYoungestCandidateInQueue();
        }
    }

    /**
     * <p>
     * This method contains the logic for the candidate solidification, that gets executed in a separate
     * {@link Thread}.
     * </p>
     * <p>
     * It executes the necessary steps periodically while waiting a short time to give the nodes the ability to
     * answer to the issued transaction requests.
     * </p>
     */
    private void candidateSolidificationThread() {
        processNewlyAddedCandidates();
        processSolidificationQueue();
        refillSolidificationQueue();
    }

    /**
     * <p>
     * This method processes the newly added candidates.
     * </p>
     * <p>
     * We process them lazy to decrease the synchronization requirements and speed up the addition of candidates from
     * outside {@link Thread}s.
     * </p>
     * <p>
     * It iterates over the candidates and adds them to the pool. If they are older than the
     * {@link #youngestCandidateInQueue}, we add the to the solidification queue.
     * </p>
     */
    private void processNewlyAddedCandidates() {
        for (Iterator<Map.Entry<Hash, Integer>> iterator = newlyAddedCandidates.entrySet().iterator();
             !Thread.currentThread().isInterrupted() && iterator.hasNext();) {

            Map.Entry<Hash, Integer> currentEntry = iterator.next();

            unsolidCandidatesPool.put(currentEntry.getKey(), currentEntry.getValue());

            if (youngestCandidateInQueue == null || currentEntry.getValue() < youngestCandidateInQueue.getValue()) {
                addToSolidificationQueue(currentEntry);
            }

            iterator.remove();
        }
    }

    /**
     * <p>
     * This method contains the logic for processing the {@link #candidatesToSolidify}.
     * </p>
     * <p>
     * It iterates through the queue and checks if the corresponding candidates are still relevant for our node, or if
     * they could be successfully solidified. If the candidates become solid or irrelevant, we remove them from the
     * pool and the queue and reset the {@link #youngestCandidateInQueue} marker (if necessary).
     * </p>
     */
    private void processSolidificationQueue() {
        for (Iterator<Map.Entry<Hash, Integer>> iterator = candidatesToSolidify.entrySet().iterator();
             !Thread.currentThread().isInterrupted() && iterator.hasNext();) {

            Map.Entry<Hash, Integer> currentEntry = iterator.next();

            if (currentEntry.getValue() <= snapshotProvider.getInitialSnapshot().getIndex() || isSolid(currentEntry)) {
                unsolidCandidatesPool.remove(currentEntry.getKey());
                iterator.remove();

                if (youngestCandidateInQueue != null &&
                        currentEntry.getKey().equals(youngestCandidateInQueue.getKey())) {

                    youngestCandidateInQueue = null;
                }
            }
        }
    }

    /**
     * <p>
     * This method takes care of adding new candidates from the pool to the solidification queue, and filling it up
     * again after it was processed / emptied before.
     * </p>
     * <p>
     * It first updates the {@link #youngestCandidateInQueue} marker and then just adds new candidates as long as there
     * is still space in the {@link #candidatesToSolidify} queue.
     * </p>
     */
    private void refillSolidificationQueue() {
        if(youngestCandidateInQueue == null && !candidatesToSolidify.isEmpty()) {
            determineYoungestCandidateInQueue();
        }

        Map.Entry<Hash, Integer> nextSolidificationCandidate;
        while (!Thread.currentThread().isInterrupted() && candidatesToSolidify.size() < SOLIDIFICATION_QUEUE_SIZE &&
                (nextSolidificationCandidate = getNextSolidificationCandidate()) != null) {

            addToSolidificationQueue(nextSolidificationCandidate);
        }
    }

    /**
     * <p>
     * This method determines the youngest candidate in the solidification queue.
     * </p>
     * <p>
     * It iterates over all candidates in the Queue and keeps track of the youngest one found (the one with the highest
     * candidate index).
     * </p>
     */
    private void determineYoungestCandidateInQueue() {
        youngestCandidateInQueue = null;
        for (Map.Entry<Hash, Integer> currentEntry : candidatesToSolidify.entrySet()) {
            if (youngestCandidateInQueue == null || currentEntry.getValue() > youngestCandidateInQueue.getValue()) {
                youngestCandidateInQueue = currentEntry;
            }
        }
    }

    /**
     * <p>
     * This method returns the earliest seen Candidate from the unsolid candidates pool, that is not part of the
     * {@link #candidatesToSolidify} queue yet.
     * </p>
     * <p>
     * It simply iterates over all candidates in the pool and looks for the one with the lowest index, that is not
     * getting actively solidified, yet.
     * </p>
     *
     * @return the Map.Entry holding the earliest candidate or null if the pool does not contain any new candidates.
     */
    private Map.Entry<Hash, Integer> getNextSolidificationCandidate() {
        Map.Entry<Hash, Integer> nextSolidificationCandidate = null;
        for (Map.Entry<Hash, Integer> candidateEntry : unsolidCandidatesPool.entrySet()) {
            if (!candidatesToSolidify.containsKey(candidateEntry.getKey()) && (nextSolidificationCandidate == null ||
                    candidateEntry.getValue() < nextSolidificationCandidate.getValue())) {

                nextSolidificationCandidate = candidateEntry;
            }
        }

        return nextSolidificationCandidate;
    }

    /**
     * <p>
     * This method performs the actual solidity check on the selected candidate.
     * </p>
     * <p>
     * It first dumps a log message to keep the node operator informed about the progress of solidification, and then
     * issues the {@link TransactionValidator#checkSolidity(Hash, boolean, int)} call that starts the solidification
     * process.
     * </p>
     * <p>
     * We limit the amount of transactions that may be processed during the solidity check, since we want to solidify
     * from the oldest candidate to the newest one and not "block" the solidification with a very recent candidate that
     * needs to traverse huge chunks of the tangle. The main goal of this is to give the solidification just enough
     * "resources" to discover the previous candidate while at the same time allowing fast solidity checks.
     * </p>
     *
     * @param currentEntry candidate entry that shall be checked
     * @return true if the given candidate is solid or false otherwise
     */
    private boolean isSolid(Map.Entry<Hash, Integer> currentEntry) {
        if (unsolidCandidatesPool.size() > 1) {
            log.info("Solidifying candidate #" + currentEntry.getValue() +
                    " [" + candidatesToSolidify.size() + " / " + unsolidCandidatesPool.size() + "]");
        }

        try {
            return transactionValidator.checkSolidity(currentEntry.getKey(), true,
                    SOLIDIFICATION_TRANSACTIONS_LIMIT);
        } catch (Exception e) {
            log.error("Error while solidifying candidate #" + currentEntry.getValue(), e);

            return false;
        }
    }
}
