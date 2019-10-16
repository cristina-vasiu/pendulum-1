package net.helix.pendulum;

import net.helix.pendulum.crypto.Merkle;
import net.helix.pendulum.crypto.Sha3;
import net.helix.pendulum.crypto.Sponge;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.crypto.Winternitz;
import net.helix.pendulum.crypto.merkle.MerkleTree;
import net.helix.pendulum.crypto.merkle.impl.MerkleTreeImpl;
import net.helix.pendulum.model.Hash;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.Arrays;

public class SignedFiles {

    public static boolean isFileSignatureValid(String filename, String signatureFilename, String publicKey, int depth, int index, int security) throws IOException {
        byte[] signature = digestFile(filename, SpongeFactory.create(SpongeFactory.Mode.S256));
        return validateSignature(signatureFilename, publicKey, depth, index, signature, security);
    }

    private static boolean validateSignature(String signatureFilename, String publicKey, int depth, int index, byte[] digest, int security) throws IOException {
        //validate signature
        SpongeFactory.Mode mode = SpongeFactory.Mode.S256;
        byte[] digests = new byte[0];
        byte[] bundle = Winternitz.normalizedBundle(digest);
        byte[] root;
        int i;
        MerkleTree merkle = new MerkleTreeImpl();
        try (InputStream inputStream = SignedFiles.class.getResourceAsStream(signatureFilename);
             BufferedReader reader = new BufferedReader((inputStream == null)
                 ? new FileReader(signatureFilename) : new InputStreamReader(inputStream))) {

            String line;
            for (i = 0; i < security && (line = reader.readLine()) != null; i++) {
                byte[] lineBytes = Hex.decode(line);
                byte[] bundleFragment = Arrays.copyOfRange(bundle, i * 16, (i + 1) * 16); //todo size
                byte[] winternitzDigest = Winternitz.digest(mode, bundleFragment, lineBytes);
                digests = ArrayUtils.addAll(digests, winternitzDigest);
            }

            if ((line = reader.readLine()) != null) {
                byte[] lineBytes = Hex.decode(line);
                root = merkle.getMerkleRoot(mode, Winternitz.address(mode, digests), lineBytes, 0, index, depth);

            } else {
                root = Winternitz.address(mode, digests);
            }
            byte[] pubkeyBytes = Hex.decode(publicKey);
            return Arrays.equals(pubkeyBytes, root); // valid
        }
    }

    private static byte[] digestFile(String filename, Sponge sha3) throws IOException {
        try (InputStream inputStream = SignedFiles.class.getResourceAsStream(filename);
             BufferedReader reader = new BufferedReader((inputStream == null)
                 ? new FileReader(filename) : new InputStreamReader(inputStream))) {
            // building snapshot message
            StringBuilder sb = new StringBuilder();
            reader.lines().forEach(line -> {
                String hex = line; // can return a null
                if (hex == null) {
                    throw new IllegalArgumentException("BYTES ARE NULL. INPUT= '" + line + "'");
                }
                sb.append(hex);
            });
            // pad snapshot message to a multiple of 32 and convert into bytes
            byte[] messageBytes = sb.toString().getBytes();
            if (messageBytes.length == 0){
                messageBytes = Hash.NULL_HASH.bytes();
            }
            int requiredLength = (int) Math.ceil(messageBytes.length / 32.0) * 32;
            byte[] finalizedMessage = MerkleTree.padding(messageBytes, requiredLength);
            // crypto snapshot message
            sha3.absorb(finalizedMessage, 0, finalizedMessage.length);
            byte[] signature = new byte[Sha3.HASH_LENGTH];
            sha3.squeeze(signature, 0, Sha3.HASH_LENGTH);
            return signature;
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}