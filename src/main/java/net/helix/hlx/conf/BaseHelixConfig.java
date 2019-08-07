package net.helix.hlx.conf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.helix.hlx.model.HashFactory;
import org.apache.commons.lang3.ArrayUtils;

import net.helix.hlx.HLX;
import net.helix.hlx.utils.HelixUtils;
import net.helix.hlx.model.Hash;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;
/*
 Note: the fields in this class are being deserialized from Jackson so they must follow Java Bean convention.
 Meaning that every field must have a getter that is prefixed with `get` unless it is a boolean and then it should be
 prefixed with `is`.
*/

public abstract class BaseHelixConfig implements HelixConfig {
    protected static final String SPLIT_STRING_TO_LIST_REGEX = ",| ";

    private boolean help;

    //API
    protected int port = Defaults.API_PORT;
    protected String apiHost = Defaults.API_HOST;
    protected List<String> remoteLimitApi = Defaults.REMOTE_LIMIT_API;
    protected List<InetAddress> remoteTrustedApiHosts = Defaults.REMOTE_LIMIT_API_HOSTS;
    protected int maxFindTransactions = Defaults.MAX_FIND_TRANSACTIONS;
    protected int maxRequestsList = Defaults.MAX_REQUESTS_LIST;
    protected int maxGetTransactionStrings = Defaults.MAX_GET_TRANSACTION_STRINGS;
    protected int maxBodyLength = Defaults.MAX_BODY_LENGTH;
    protected String remoteAuth = Defaults.REMOTE_AUTH;
    protected boolean powDisabled = Defaults.IS_POW_DISABLED;

    //We don't have a REMOTE config but we have a remote flag. We must add a field for JCommander
    private boolean remote;

    //Network
    protected int udpReceiverPort = Defaults.UDP_RECEIVER_PORT;
    protected int tcpReceiverPort = Defaults.TCP_RECEIVER_PORT;
    protected double pRemoveRequest = Defaults.P_REMOVE_REQUEST;
    protected double pDropCacheEntry = Defaults.P_DROP_CACHE_ENTRY;
    protected int sendLimit = Defaults.SEND_LIMIT;
    protected int maxPeers = Defaults.MAX_PEERS;
    protected boolean dnsRefresherEnabled = Defaults.DNS_REFRESHER_ENABLED;
    protected boolean dnsResolutionEnabled = Defaults.DNS_RESOLUTION_ENABLED;
    protected List<String> neighbors = new ArrayList<>();

    //XI
    protected String xiDir = Defaults.XI_DIR;

    //DB
    protected String dbPath = Defaults.DB_PATH;
    protected String dbLogPath = Defaults.DB_LOG_PATH;
    protected int dbCacheSize = Defaults.DB_CACHE_SIZE; //KB
    protected String mainDb = Defaults.ROCKS_DB;
    protected boolean revalidate = Defaults.REVALIDATE;
    protected boolean rescanDb = Defaults.RESCAN_DB;

    //Protocol
    protected double pReplyRandomTip = Defaults.P_REPLY_RANDOM_TIP;
    protected double pDropTransaction = Defaults.P_DROP_TRANSACTION;
    protected double pSelectMilestoneChild = Defaults.P_SELECT_MILESTONE_CHILD;
    protected double pSendMilestone = Defaults.P_SEND_MILESTONE;
    protected double pPropagateRequest = Defaults.P_PROPAGATE_REQUEST;

    //ZMQ
    protected boolean zmqEnableTcp = Defaults.ZMQ_ENABLE_TCP;
    protected boolean zmqEnableIpc = Defaults.ZMQ_ENABLE_IPC;
    protected int zmqPort = Defaults.ZMQ_PORT;
    protected int zmqThreads = Defaults.ZMQ_THREADS;
    protected String zmqIpc = Defaults.ZMQ_IPC;
    protected int qSizeNode = Defaults.QUEUE_SIZE;
    protected int cacheSizeBytes = Defaults.CACHE_SIZE_BYTES;
    /**
     * @deprecated This field was replaced by {@link #zmqEnableTcp} and {@link #zmqEnableIpc}. It is only needed
     * for backward compatibility to --zmq-enabled parameter with JCommander.
     */
    @Deprecated
    private boolean zmqEnabled;

    //Graphstream
    protected  boolean graphEnabled = Defaults.GRAPH_ENABLED;

    //Tip Selection
    protected int maxDepth = Defaults.MAX_DEPTH;
    protected double alpha = Defaults.ALPHA;
    private int maxAnalyzedTransactions = Defaults.MAX_ANALYZED_TXS;

    //Tip Solidification
    protected boolean tipSolidifierEnabled = Defaults.TIP_SOLIDIFIER_ENABLED;

    //PoW
    protected int powThreads = Defaults.POW_THREADS;

    //Snapshot
    protected boolean localSnapshotsEnabled = Defaults.LOCAL_SNAPSHOTS_ENABLED;
    protected boolean localSnapshotsPruningEnabled = Defaults.LOCAL_SNAPSHOTS_PRUNING_ENABLED;
    protected int localSnapshotsPruningDelay = Defaults.LOCAL_SNAPSHOTS_PRUNING_DELAY;
    protected int localSnapshotsIntervalSynced = Defaults.LOCAL_SNAPSHOTS_INTERVAL_SYNCED;
    protected int localSnapshotsIntervalUnsynced = Defaults.LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED;
    protected int localSnapshotsDepth = Defaults.LOCAL_SNAPSHOTS_DEPTH;
    protected String localSnapshotsBasePath = Defaults.LOCAL_SNAPSHOTS_BASE_PATH;

    //Logging
    protected boolean saveLogEnabled = Defaults.SAVELOG_ENABLED;
    protected String saveLogBasePath = Defaults.SAVELOG_BASE_PATH;
    protected String saveLogXMLFile = Defaults.SAVELOG_XML_FILE;

    //Curator
    protected boolean curatorEnabled = Defaults.CURATOR_ENABLED;
    protected Hash curatorAddress = Defaults.CURATOR_ADDRESS;
    protected int updateNomineeDelay = Defaults.UPDATE_NOMINEE_DELAY;
    protected int startRoundDelay = Defaults.START_ROUND_DELAY;
    protected int curatorKeyDepth = Defaults.CURATOR_KEY_DEPTH;

    //Milestone
    protected boolean nomineeEnabled = Defaults.NOMINEE_ENABLED;
    protected Set<Hash> initialNominees = Defaults.INITIAL_NOMINEES;
    protected long genesisTime = Defaults.GENESIS_TIME;
    protected int roundDuration = Defaults.ROUND_DURATION;
    protected int milestoneKeyDepth = Defaults.MILESTONE_KEY_DEPTH;

    //Spammer
    protected int spamDelay = Defaults.SPAM_DELAY;

    public BaseHelixConfig() {
        //empty constructor
    }

    @Override
    public JCommander parseConfigFromArgs(String[] args) throws ParameterException {
        //One can invoke help via INI file (feature/bug) so we always create JCommander even if args is empty
        JCommander jCommander = JCommander.newBuilder()
                .addObject(this)
                //This is in order to enable the `--conf` and `--testnet` option
                .acceptUnknownOptions(true)
                .allowParameterOverwriting(true)
                //This is the first line of JCommander Usage
                .programName("java -jar hlx-" + HLX.VERSION + ".jar")
                .build();
        if (ArrayUtils.isNotEmpty(args)) {
            jCommander.parse(args);
        }
        return jCommander;
    }

    @Override
    public boolean isHelp() {
        return help;
    }

    @JsonProperty
    @Parameter(names = {"--help", "-h"} , help = true, hidden = true)
    public void setHelp(boolean help) {
        this.help = help;
    }

    @Override
    public int getPort() {
        return port;
    }

    @JsonProperty
    @Parameter(names = {"--port", "-p"}, description = APIConfig.Descriptions.PORT)
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String getApiHost() {
        return apiHost;
    }

    @JsonProperty
    @Parameter(names = {"--api-host"}, description = APIConfig.Descriptions.API_HOST)
    protected void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    @JsonIgnore
    @Parameter(names = {"--remote"}, description = APIConfig.Descriptions.REMOTE)
    protected void setRemote(boolean remote) {
        this.apiHost = "0.0.0.0";
    }

    @Override
    public List<String> getRemoteLimitApi() {
        return remoteLimitApi;
    }

    @JsonProperty
    @Parameter(names = {"--remote-limit-api"}, description = APIConfig.Descriptions.REMOTE_LIMIT_API)
    protected void setRemoteLimitApi(String remoteLimitApi) {
        this.remoteLimitApi = HelixUtils.splitStringToImmutableList(remoteLimitApi, SPLIT_STRING_TO_LIST_REGEX);
    }

    @Override
    public List<InetAddress> getRemoteTrustedApiHosts() {
        return remoteTrustedApiHosts;
    }

    @JsonProperty
    @Parameter(names = {"--remote-trusted-api-hosts"}, description = APIConfig.Descriptions.REMOTE_TRUSTED_API_HOSTS)
    public void setRemoteTrustedApiHosts(String remoteTrustedApiHosts) {
        List<String> addresses = HelixUtils.splitStringToImmutableList(remoteTrustedApiHosts, SPLIT_STRING_TO_LIST_REGEX);
        List<InetAddress> inetAddresses = addresses.stream().map(host -> {
            try {
                return InetAddress.getByName(host.trim());
            } catch (UnknownHostException e) {
                throw new ParameterException("Invalid value for --remote-trusted-api-hosts address: ", e);
            }
        }).collect(Collectors.toList());

        // always make sure that localhost exists as trusted host
        if(!inetAddresses.contains(Defaults.REMOTE_LIMIT_API_DEFAULT_HOST)) {
            inetAddresses.add(Defaults.REMOTE_LIMIT_API_DEFAULT_HOST);
        }
        this.remoteTrustedApiHosts = Collections.unmodifiableList(inetAddresses);
    }

    @Override
    public int getMaxFindTransactions() {
        return maxFindTransactions;
    }

    @JsonProperty
    @Parameter(names = {"--max-find-transactions"}, description = APIConfig.Descriptions.MAX_FIND_TRANSACTIONS)
    protected void setMaxFindTransactions(int maxFindTransactions) {
        this.maxFindTransactions = maxFindTransactions;
    }

    @Override
    public int getMaxRequestsList() {
        return maxRequestsList;
    }

    @JsonProperty
    @Parameter(names = {"--max-requests-list"}, description = APIConfig.Descriptions.MAX_REQUESTS_LIST)
    protected void setMaxRequestsList(int maxRequestsList) {
        this.maxRequestsList = maxRequestsList;
    }

    @Override
    public int getMaxTransactionStrings() {
        return maxGetTransactionStrings;
    }

    @JsonProperty
    @Parameter(names = {"--max-get-transaction-strings"}, description = APIConfig.Descriptions.MAX_GET_TRANSACTION_STRINGS)
    protected void setMaxGetTransactionStrings(int maxGetTransactionStrings) {
        this.maxGetTransactionStrings = maxGetTransactionStrings;
    }

    @Override
    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    @JsonProperty
    @Parameter(names = {"--max-body-length"}, description = APIConfig.Descriptions.MAX_BODY_LENGTH)
    protected void setMaxBodyLength(int maxBodyLength) {
        this.maxBodyLength = maxBodyLength;
    }

    @Override
    public String getRemoteAuth() {
        return remoteAuth;
    }

    @JsonProperty
    @Parameter(names = {"--remote-auth"}, description = APIConfig.Descriptions.REMOTE_AUTH)
    protected void setRemoteAuth(String remoteAuth) {
        this.remoteAuth = remoteAuth;
    }

    @Override
    public int getUdpReceiverPort() {
        return udpReceiverPort;
    }

    @JsonProperty
    @Parameter(names = {"-u", "--udp-receiver-port"}, description = NetworkConfig.Descriptions.UDP_RECEIVER_PORT)
    public void setUdpReceiverPort(int udpReceiverPort) {
        this.udpReceiverPort = udpReceiverPort;
    }

    @Override
    public int getTcpReceiverPort() {
        return tcpReceiverPort;
    }

    @JsonProperty
    @Parameter(names = {"-t", "--tcp-receiver-port"}, description = NetworkConfig.Descriptions.TCP_RECEIVER_PORT)
    protected void setTcpReceiverPort(int tcpReceiverPort) {
        this.tcpReceiverPort = tcpReceiverPort;
    }

    @Override
    public double getpRemoveRequest() {
        return pRemoveRequest;
    }

    @JsonProperty
    @Parameter(names = {"--p-remove-request"}, description = NetworkConfig.Descriptions.P_REMOVE_REQUEST)
    protected void setpRemoveRequest(double pRemoveRequest) {
        this.pRemoveRequest = pRemoveRequest;
    }

    @Override
    public int getSendLimit() {
        return sendLimit;
    }

    @JsonProperty
    @Parameter(names = {"--send-limit"}, description = NetworkConfig.Descriptions.SEND_LIMIT)
    protected void setSendLimit(int sendLimit) {
        this.sendLimit = sendLimit;
    }

    @Override
    public int getMaxPeers() {
        return maxPeers;
    }

    @JsonProperty
    @Parameter(names = {"--max-peers"}, description = NetworkConfig.Descriptions.MAX_PEERS)
    protected void setMaxPeers(int maxPeers) {
        this.maxPeers = maxPeers;
    }

    @Override
    public boolean isDnsRefresherEnabled() {
        return dnsRefresherEnabled;
    }

    @JsonProperty
    @Parameter(names = {"--dns-refresher"}, description = NetworkConfig.Descriptions.DNS_REFRESHER_ENABLED, arity = 1)
    protected void setDnsRefresherEnabled(boolean dnsRefresherEnabled) {
        this.dnsRefresherEnabled = dnsRefresherEnabled;
    }

    @Override
    public boolean isDnsResolutionEnabled() {
        return dnsResolutionEnabled;
    }

    @JsonProperty
    @Parameter(names = {"--dns-resolution"}, description = NetworkConfig.Descriptions.DNS_RESOLUTION_ENABLED, arity = 1)
    protected void setDnsResolutionEnabled(boolean dnsResolutionEnabled) {
        this.dnsResolutionEnabled = dnsResolutionEnabled;
    }

    @Override
    public List<String> getNeighbors() {
        return neighbors;
    }

    @JsonProperty
    @Parameter(names = {"-n", "--neighbors"}, description = NetworkConfig.Descriptions.NEIGHBORS)
    protected void setNeighbors(String neighbors) {
        this.neighbors = HelixUtils.splitStringToImmutableList(neighbors, SPLIT_STRING_TO_LIST_REGEX);
    }

    @Override
    public String getXiDir() {
        return xiDir;
    }

    @JsonProperty
    @Parameter(names = {"--XI-dir"}, description = XIConfig.Descriptions.XI_DIR)
    protected void setXiDir(String xiDir) {
        this.xiDir = xiDir;
    }

    @Override
    public String getDbPath() {
        return dbPath;
    }

    @JsonProperty
    @Parameter(names = {"--db-path"}, description = DbConfig.Descriptions.DB_PATH)
    protected void setDbPath(String dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public String getDbLogPath() {
        return dbLogPath;
    }

    @JsonProperty
    @Parameter(names = {"--db-log-path"}, description = DbConfig.Descriptions.DB_LOG_PATH)
    protected void setDbLogPath(String dbLogPath) {
        this.dbLogPath = dbLogPath;
    }

    @Override
    public int getDbCacheSize() {
        return dbCacheSize;
    }

    @JsonProperty
    @Parameter(names = {"--db-cache-size"}, description = DbConfig.Descriptions.DB_CACHE_SIZE)
    protected void setDbCacheSize(int dbCacheSize) {
        this.dbCacheSize = dbCacheSize;
    }

    @Override
    public String getMainDb() {
        return mainDb;
    }

    @JsonProperty
    @Parameter(names = {"--db"}, description = DbConfig.Descriptions.MAIN_DB)
    protected void setMainDb(String mainDb) {
        this.mainDb = mainDb;
    }

    @Override
    public boolean isRevalidate() {
        return revalidate;
    }

    @JsonProperty
    @Parameter(names = {"--revalidate"}, description = DbConfig.Descriptions.REVALIDATE)
    protected void setRevalidate(boolean revalidate) {
        this.revalidate = revalidate;
    }

    @Override
    public boolean isRescanDb() {
        return rescanDb;
    }

    @JsonProperty
    @Parameter(names = {"--rescan"}, description = DbConfig.Descriptions.RESCAN_DB)
    protected void setRescanDb(boolean rescanDb) {
        this.rescanDb = rescanDb;
    }

    @Override
    public int getMwm() {
        return Defaults.MWM;
    }

    @Override
    public int getTransactionPacketSize() {
        return Defaults.PACKET_SIZE;
    }

    @Override
    public int getRequestHashSize() {
        return Defaults.REQ_HASH_SIZE;
    }

    @Override
    public double getpReplyRandomTip() {
        return pReplyRandomTip;
    }

    @JsonProperty
    @Parameter(names = {"--p-reply-random"}, description = ProtocolConfig.Descriptions.P_REPLY_RANDOM_TIP)
    protected void setpReplyRandomTip(double pReplyRandomTip) {
        this.pReplyRandomTip = pReplyRandomTip;
    }

    @Override
    public double getpDropTransaction() {
        return pDropTransaction;
    }

    @JsonProperty
    @Parameter(names = {"--p-drop-transaction"}, description = ProtocolConfig.Descriptions.P_DROP_TRANSACTION)
    protected void setpDropTransaction(double pDropTransaction) {
        this.pDropTransaction = pDropTransaction;
    }

    @Override
    public double getpSelectMilestoneChild() {
        return pSelectMilestoneChild;
    }

    @JsonProperty
    @Parameter(names = {"--p-select-milestone"}, description = ProtocolConfig.Descriptions.P_SELECT_MILESTONE)
    protected void setpSelectMilestoneChild(double pSelectMilestoneChild) {
        this.pSelectMilestoneChild = pSelectMilestoneChild;
    }

    @Override
    public double getpSendMilestone() {
        return pSendMilestone;
    }

    @JsonProperty
    @Parameter(names = {"--p-send-milestone"}, description = ProtocolConfig.Descriptions.P_SEND_MILESTONE)
    protected void setpSendMilestone(double pSendMilestone) {
        this.pSendMilestone = pSendMilestone;
    }

    @Override
    public double getpPropagateRequest() {
        return pPropagateRequest;
    }

    @JsonProperty
    @Parameter(names = {"--p-propagate-request"}, description = ProtocolConfig.Descriptions.P_PROPAGATE_REQUEST)
    protected void setpPropagateRequest(double pPropagateRequest) {
        this.pPropagateRequest = pPropagateRequest;
    }

    @Override
    public boolean getLocalSnapshotsEnabled() {
        return this.localSnapshotsEnabled;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-enabled"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_ENABLED)
    protected void setLocalSnapshotsEnabled(boolean localSnapshotsEnabled) {
        this.localSnapshotsEnabled = localSnapshotsEnabled;
    }

    @Override
    public boolean getLocalSnapshotsPruningEnabled() {
        return this.localSnapshotsPruningEnabled;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-pruning-enabled"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_PRUNING_ENABLED)
    protected void setLocalSnapshotsPruningEnabled(boolean localSnapshotsPruningEnabled) {
        this.localSnapshotsPruningEnabled = localSnapshotsPruningEnabled;
    }

    @Override
    public int getLocalSnapshotsPruningDelay() {
        return this.localSnapshotsPruningDelay;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-pruning-delay"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_PRUNING_DELAY)
    protected void setLocalSnapshotsPruningDelay(int localSnapshotsPruningDelay) {
        this.localSnapshotsPruningDelay = localSnapshotsPruningDelay;
    }

    @Override
    public int getLocalSnapshotsIntervalSynced() {
        return this.localSnapshotsIntervalSynced;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-interval-synced"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_INTERVAL_SYNCED)
    protected void setLocalSnapshotsIntervalSynced(int localSnapshotsIntervalSynced) {
        this.localSnapshotsIntervalSynced = localSnapshotsIntervalSynced;
    }

    @Override
    public int getLocalSnapshotsIntervalUnsynced() {
        return this.localSnapshotsIntervalUnsynced;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-interval-unsynced"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED)
    protected void setLocalSnapshotsIntervalUnsynced(int localSnapshotsIntervalUnsynced) {
        this.localSnapshotsIntervalUnsynced = localSnapshotsIntervalUnsynced;
    }

    @Override
    public int getLocalSnapshotsDepth() {
        return this.localSnapshotsDepth;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-depth"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_DEPTH)
    protected void setLocalSnapshotsDepth(int localSnapshotsDepth) {
        this.localSnapshotsDepth = localSnapshotsDepth;
    }

    @Override
    public String getLocalSnapshotsBasePath() {
        return this.localSnapshotsBasePath;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-base-path"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_BASE_PATH)
    protected void setLocalSnapshotsBasePath(String localSnapshotsBasePath) {
        this.localSnapshotsBasePath = localSnapshotsBasePath;
    }

    @Override
    public long getSnapshotTime() {
        return Defaults.GLOBAL_SNAPSHOT_TIME;
    }

    @Override
    public String getSnapshotFile() {
        return Defaults.SNAPSHOT_FILE;
    }

    @Override
    public String getSnapshotSignatureFile() {
        return Defaults.SNAPSHOT_SIG_FILE;
    }

    @Override
    public String getPreviousEpochSpentAddressesFiles() {
        return Defaults.PREVIOUS_EPOCHS_SPENT_ADDRESSES_TXT;
    }

    @Override
    public String getPreviousEpochSpentAddressesSigFile() {
        return Defaults.PREVIOUS_EPOCHS_SPENT_ADDRESSES_SIG;
    }

    @Override
    public int getMilestoneStartIndex() {
        return Defaults.MILESTONE_START_INDEX;
    }

    @Override
    public int getNumberOfKeysInMilestone() {
        return Defaults.NUM_KEYS_IN_MILESTONE;
    }

    /**
     * Checks if ZMQ is enabled.
     * @return true if zmqEnableTcp or zmqEnableIpc is set.
     */
    @Override
    public boolean isZmqEnabled() {
        return zmqEnableTcp || zmqEnableIpc;
    }

    /**
     * Activates ZMQ to listen on TCP and IPC.
     * @deprecated Use {@link #setZmqEnableTcp(boolean) and/or {@link #setZmqEnableIpc(boolean)}} instead.
     * @param zmqEnabled true if ZMQ should listen in TCP and IPC.
     */
    @Deprecated
    @JsonProperty
    @Parameter(names = "--zmq-enabled", description = ZMQConfig.Descriptions.ZMQ_ENABLED, arity = 1)
    protected void setZmqEnabled(boolean zmqEnabled) {
        this.zmqEnableTcp = zmqEnabled;
        this.zmqEnableIpc = zmqEnabled;
    }

    @Override
    public boolean isZmqEnableTcp() {
        return zmqEnableTcp;
    }

    @JsonProperty
    @Parameter(names = "--zmq-enable-tcp", description = ZMQConfig.Descriptions.ZMQ_ENABLE_TCP, arity = 1)
    public void setZmqEnableTcp(boolean zmqEnableTcp) {
        this.zmqEnableTcp = zmqEnableTcp;
    }

    @Override
    public boolean isZmqEnableIpc() {
        return zmqEnableIpc;
    }

    @JsonProperty
    @Parameter(names = "--zmq-enable-ipc", description = ZMQConfig.Descriptions.ZMQ_ENABLE_IPC, arity = 1)
    public void setZmqEnableIpc(boolean zmqEnableIpc) {
        this.zmqEnableIpc = zmqEnableIpc;
    }

    @Override
    public int getZmqPort() {
        return zmqPort;
    }

    @JsonProperty
    @Parameter(names = "--zmq-port", description = ZMQConfig.Descriptions.ZMQ_PORT)
    protected void setZmqPort(int zmqPort) {
        this.zmqPort = zmqPort;
        this.zmqEnableTcp = true;
    }

    @Override
    public int getZmqThreads() {
        return zmqThreads;
    }

    @JsonProperty
    @Parameter(names = "--zmq-threads", description = ZMQConfig.Descriptions.ZMQ_THREADS)
    protected void setZmqThreads(int zmqThreads) {
        this.zmqThreads = zmqThreads;
    }

    @Override
    public String getZmqIpc() {
        return zmqIpc;
    }

    @JsonProperty
    @Parameter(names = "--zmq-ipc", description = ZMQConfig.Descriptions.ZMQ_IPC)
    protected void setZmqIpc(String zmqIpc) {
        this.zmqIpc = zmqIpc;
        this.zmqEnableIpc = true;
    }

    @Override
    public boolean isGraphEnabled() {
        return graphEnabled;
    }

    @JsonProperty
    @Parameter(names = {"-g", "--graph-enabled"}, description = GraphConfig.Descriptions.GRAPH_ENABLED)
    protected void setGraphEnabled(boolean graphEnabled) {
        this.graphEnabled = graphEnabled;
    }

    @Override
    public int getqSizeNode() {
        return qSizeNode;
    }

    @JsonProperty
    @Parameter(names = "--queue-size", description = NetworkConfig.Descriptions.Q_SIZE_NODE)
    protected void setqSizeNode(int qSizeNode) {
        this.qSizeNode = qSizeNode;
    }

    @Override
    public double getpDropCacheEntry() {
        return pDropCacheEntry;
    }

    @JsonProperty
    @Parameter(names = "--p-drop-cache", description = NetworkConfig.Descriptions.P_DROP_CACHE_ENTRY)
    protected void setpDropCacheEntry(double pDropCacheEntry) {
        this.pDropCacheEntry = pDropCacheEntry;
    }

    @Override
    public int getCacheSizeBytes() {
        return cacheSizeBytes;
    }

    @JsonProperty
    @Parameter(names = "--cache-size", description = NetworkConfig.Descriptions.CACHE_SIZE_BYTES)
    protected void setCacheSizeBytes(int cacheSizeBytes) {
        this.cacheSizeBytes = cacheSizeBytes;
    }

    @Override
    public int getMaxDepth() {
        return maxDepth;
    }

    @JsonProperty
    @Parameter(names = "--max-depth", description = TipSelConfig.Descriptions.MAX_DEPTH)
    protected void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    @JsonProperty("TIPSELECTION_ALPHA")
    @Parameter(names = "--alpha", description = TipSelConfig.Descriptions.ALPHA)
    protected void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public boolean isTipSolidifierEnabled() {
        return tipSolidifierEnabled;
    }

    @Override
    public int getBelowMaxDepthTransactionLimit() {
        return maxAnalyzedTransactions;
    }

    @JsonProperty
    @Parameter(names = "--max-analyzed-transactions", description = TipSelConfig.Descriptions.BELOW_MAX_DEPTH_TRANSACTION_LIMIT)
    protected void setBelowMaxDepthTransactionLimit(int maxAnalyzedTransactions) {
        this.maxAnalyzedTransactions = maxAnalyzedTransactions;
    }

    // Curator
    @Override
    public boolean getCuratorEnabled() {return curatorEnabled; }
    @JsonProperty
    @Parameter(names = {"--curator"}, description = CuratorConfig.Descriptions.CURATOR_ENABLED, arity = 1)
    protected void setCuratorEnabled(boolean curatorEnabled) { this.curatorEnabled = curatorEnabled; }

    @Override
    public Hash getCuratorAddress() { return curatorAddress; }

    @Override
    public boolean isDontValidateTestnetCuratorSig() { return false; }

    @Override
    public int getUpdateNomineeDelay() {return updateNomineeDelay; }
    @JsonProperty
    @Parameter(names = {"--update-nominee"}, description = CuratorConfig.Descriptions.UPDATE_NOMINEE_DELAY)
    protected void setUpdateNomineeDelay(int updateNomineeDelay) { this.updateNomineeDelay = updateNomineeDelay; }

    @Override
    public int getStartRoundDelay() {return startRoundDelay; }
    @JsonProperty
    @Parameter(names = {"--start-nominee"}, description = CuratorConfig.Descriptions.START_ROUND_DELAY)
    protected void setStartRoundDelay(int startRoundDelay) { this.startRoundDelay = startRoundDelay; }

    @Override
    public int getCuratorKeyDepth() {return curatorKeyDepth; }

    // Milestone
    @Override
    public boolean getNomineeEnabled() {return nomineeEnabled; }
    @JsonProperty
    @Parameter(names = {"--nominee"}, description = MilestoneConfig.Descriptions.NOMINEE_ENABLED, arity = 1)
    protected void setNomineeEnabled(boolean nomineeEnabled) { this.nomineeEnabled = nomineeEnabled; }

    @Override
    public Set<Hash> getInitialNominees() {return initialNominees; }

    @Override
    public boolean isDontValidateTestnetMilestoneSig() {
        return false;
    }

    @Override
    public long getGenesisTime() {
        return genesisTime;
    }
    @JsonProperty
    @Parameter(names = {"--genesis"}, description = MilestoneConfig.Descriptions.GENESIS_TIME)
    protected void setGenesisTime(int genesisTime) { this.genesisTime = genesisTime; }

    @Override
    public int getRoundDuration() {
        return roundDuration;
    }
    @JsonProperty
    @Parameter(names = {"--round"}, description = MilestoneConfig.Descriptions.ROUND_DURATION)
    protected void setRoundDuration(int roundDuration) { this.roundDuration = roundDuration; }

    @Override
    public int getMilestoneKeyDepth() {return milestoneKeyDepth; }


    // POW
    @Override
    public boolean isPoWDisabled() {
        return powDisabled;
    }
    @JsonProperty
    @Parameter(names = {"--pow-disabled"}, description = APIConfig.Descriptions.IS_POW_DISABLED)
    protected void setPowDisabled(boolean powDisabled) { this.powDisabled = powDisabled; }

    @Override
    public int getPowThreads() {
        return powThreads;
    }
    @JsonProperty
    @Parameter(names = "--pow-threads", description = PoWConfig.Descriptions.POW_THREADS)
    protected void setPowThreads(int powThreads) {
        this.powThreads = powThreads;
    }

    @Override
    public boolean isSaveLogEnabled() {
        return saveLogEnabled;
    }
    @JsonProperty
    @Parameter(names = {"--savelog-enabled"}, description = LoggingConfig.Descriptions.SAVELOG_ENABLED)
    protected void setSaveLogEnabled(boolean saveLogEnabled) { this.saveLogEnabled = saveLogEnabled; }

    @Override
    public String getSaveLogBasePath() {
        return saveLogBasePath;
    }
    @JsonProperty
    @Parameter(names = {"--savelog-path"}, description = LoggingConfig.Descriptions.SAVELOG_BASE_PATH)
    protected void setSaveLogBasePath(String saveLogBasePath) { this.saveLogBasePath = saveLogBasePath; }

    @Override
    public String getSaveLogXMLFile() {
        return saveLogXMLFile;
    }
    @JsonProperty
    @Parameter(names = {"--savelog-xml"}, description = LoggingConfig.Descriptions.SAVELOG_XML_FILE)
    protected void setSaveLogXMLFile(String saveLogXMLFile) { this.saveLogXMLFile = saveLogXMLFile; }

    // Spam
    @Override
    public int getSpamDelay() {
        return spamDelay;
    }
    @JsonProperty
    @Parameter(names = {"--spam"}, description = LoggingConfig.Descriptions.SAVELOG_XML_FILE)
    protected void setSpamDelay(int spamDelay) { this.spamDelay = spamDelay; }

    public interface Defaults {
        //API
        int API_PORT = 8085;
        String API_HOST = "localhost";
        List<String> REMOTE_LIMIT_API = HelixUtils.createImmutableList(); // "addNeighbors", "getNeighbors", "removeNeighbors", "attachToTangle", "interruptAttachingToTangle" <- TODO: limit these in production!
        InetAddress REMOTE_LIMIT_API_DEFAULT_HOST = InetAddress.getLoopbackAddress();
        List<InetAddress> REMOTE_LIMIT_API_HOSTS = HelixUtils.createImmutableList(REMOTE_LIMIT_API_DEFAULT_HOST);
        int MAX_FIND_TRANSACTIONS = 100_000;
        int MAX_REQUESTS_LIST = 1_000;
        int MAX_GET_TRANSACTION_STRINGS = 10_000;
        int MAX_BODY_LENGTH = 1_000_000;
        String REMOTE_AUTH = "";
        boolean IS_POW_DISABLED = false;

        //Network
        int UDP_RECEIVER_PORT = 4100;
        int TCP_RECEIVER_PORT = 5100;
        double P_REMOVE_REQUEST = 0.01d;
        int SEND_LIMIT = -1;
        int MAX_PEERS = 0;
        boolean DNS_REFRESHER_ENABLED = true;
        boolean DNS_RESOLUTION_ENABLED = true;

        //XI
        String XI_DIR = "modules";

        //DB
        String DB_PATH = "mainnetdb";
        String DB_LOG_PATH = "mainnet.log";
        int DB_CACHE_SIZE = 100_000;
        String ROCKS_DB = "rocksdb";
        boolean REVALIDATE = false;
        boolean RESCAN_DB = false;

        //Protocol
        double P_REPLY_RANDOM_TIP = 0.66d;
        double P_DROP_TRANSACTION = 0d;
        double P_SELECT_MILESTONE_CHILD = 0.7d;
        double P_SEND_MILESTONE = 0.02d;
        double P_PROPAGATE_REQUEST = 0.01d;
        int MWM = 1;
        int PACKET_SIZE = 800;
        int REQ_HASH_SIZE = 32;
        int QUEUE_SIZE = 1_000;
        double P_DROP_CACHE_ENTRY = 0.02d;
        int CACHE_SIZE_BYTES = 150_000;

        //Zmq
        int ZMQ_THREADS = 1;
        boolean ZMQ_ENABLE_IPC = false;
        String ZMQ_IPC = "ipc://hlx";
        boolean ZMQ_ENABLE_TCP = false;
        int ZMQ_PORT = 5556;

        //Graphstream
        boolean GRAPH_ENABLED = false;

        //TipSel
        int MAX_DEPTH = 15;
        double ALPHA = 0.001d;

        //Tip solidification
        boolean TIP_SOLIDIFIER_ENABLED = true;

        //PoW
        int POW_THREADS = 8;

        //Curator
        boolean CURATOR_ENABLED = false;
        Hash CURATOR_ADDRESS = HashFactory.ADDRESS.create("2bebfaee978c03e3263c3e5480b602fb040a120768c41d8bfae6c0c124b8e82a");
        int UPDATE_NOMINEE_DELAY = 30000;
        int START_ROUND_DELAY = 5;
        int CURATOR_KEY_DEPTH = 15;

        //Milestone
        boolean NOMINEE_ENABLED = false;
        Set<Hash> INITIAL_NOMINEES = new HashSet<>(Arrays.asList(
                HashFactory.ADDRESS.create("6a8413edc634e948e3446806afde11b17e0e188faf80a59a8b1147a0600cc5db"),
                HashFactory.ADDRESS.create("cc439e031810f847e4399477e46fd12de2468f12cd0ba85447404148bee2a033")
        ));
        long GENESIS_TIME = 1565008988563L;
        int ROUND_DURATION = 5000;
        int MILESTONE_KEY_DEPTH = 10;

        //Snapshot
        boolean LOCAL_SNAPSHOTS_ENABLED = true;
        boolean LOCAL_SNAPSHOTS_PRUNING_ENABLED = true;
        int LOCAL_SNAPSHOTS_PRUNING_DELAY = 50000;
        int LOCAL_SNAPSHOTS_INTERVAL_SYNCED = 10;
        int LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED = 1000;
        String LOCAL_SNAPSHOTS_BASE_PATH = "mainnet";
        int LOCAL_SNAPSHOTS_DEPTH = 100;
        String SNAPSHOT_FILE = "/snapshotMainnet.txt";
        String SNAPSHOT_SIG_FILE = "/snapshotMainnet.sig";
        String PREVIOUS_EPOCHS_SPENT_ADDRESSES_TXT = "/previousEpochsSpentAddresses.txt";
        String PREVIOUS_EPOCHS_SPENT_ADDRESSES_SIG = "/previousEpochsSpentAddresses.sig";
        long GLOBAL_SNAPSHOT_TIME = 1522235533L;
        int MILESTONE_START_INDEX = 0;
        int NUM_KEYS_IN_MILESTONE = 10;
        int MAX_ANALYZED_TXS = 20_000;

        //Logging
        boolean SAVELOG_ENABLED = false;
        String SAVELOG_BASE_PATH = "logs/";
        String SAVELOG_XML_FILE = "/logback-save.xml";

        //Spammer
        int SPAM_DELAY = 0;
    }
}