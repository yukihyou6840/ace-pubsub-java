package se.sics.ace.coap.rs.oscoreProfile;

import java.util.logging.Logger;

import org.eclipse.californium.cose.AlgorithmID;
import org.eclipse.californium.cose.CoseException;
import org.eclipse.californium.oscore.OSCoreCtx;
import org.eclipse.californium.oscore.OSException;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;

import se.sics.ace.AceException;
import se.sics.ace.Constants;

/**
 * Utility class to parse, verify and access  OSCORE_Security_Context in a cnf element
 * 
 * @author Ludwig Seitz
 *
 */
public class OscoreSecurityContext {
    
    /**
     * The logger
     */
    private static final Logger LOGGER 
        = Logger.getLogger(OscoreSecurityContext.class.getName());
    
    /**
     * The Master_Secret
     */
    private byte[] ms;
    
    /**
     * The server identifier
     */
    private byte[] serverId;
    
    /**
     * The client identifier
     */
    private byte[] clientId;
    
    /**
     * The key derivation function, can be null for default: AES_CCM_16_64_128
     */
    private AlgorithmID hkdf;
    
    /**
     * The encryption algorithm, can be null for default: HKDF_HMAC_SHA_256
     */
    private AlgorithmID alg;
    
    /**
     * The Master Salt, can be null
     */
    private byte[] salt;

    /**
     * The replay window size
     */
    private Integer replaySize;
    
    /**
     * Constructor.
     * 
     * @param cnf  the confirmation CBORObject containing 
     *      the OSCORE Security Context.
     * 
     * @throws AceException 
     */
    public OscoreSecurityContext(CBORObject cnf) throws AceException {
        CBORObject osc = cnf.get(Constants.OSCORE_Security_Context);
        if (osc == null || !osc.getType().equals(CBORType.Map)) {
            LOGGER.info("Missing or invalid parameter type for "
                    + "'OSCORE_Security_Context', must be CBOR-map");
            throw new AceException("invalid/missing OSCORE_Security_Context");
        }
        
        CBORObject algC = osc.get(Constants.OS_ALG);
        this.alg = null;
        if (algC != null) {
            try {
                this.alg = AlgorithmID.FromCBOR(algC);
            } catch (CoseException e) {
                LOGGER.info("Invalid algorithmId: " + e.getMessage());
                throw new AceException(
                        "Malformed algorithm Id in OSCORE security context");
            }
        }
        
        CBORObject clientIdC = osc.get(Constants.OS_CLIENTID);
        if (clientIdC != null) {
            if (!clientIdC.getType().equals(CBORType.ByteString)) {
                LOGGER.info("Invalid parameter: 'clientId',"
                        + " must be byte-array");
                throw new AceException(
                        "Malformed client Id in OSCORE security context");
            }
            this.clientId= clientIdC.GetByteString(); 
        }
               
        CBORObject ctxIdC = osc.get(Constants.OS_CONTEXTID);
        if (ctxIdC != null) {
            LOGGER.info("Invalid parameter: contextID must be null");
            throw new AceException( 
                    "contextId must be null in OSCORE security context");
        }

        CBORObject kdfC = osc.get(Constants.OS_HKDF);
        if (kdfC != null) {
            try {
                this.hkdf = AlgorithmID.FromCBOR(kdfC);
            } catch (CoseException e) {
                LOGGER.info("Invalid kdf: " + e.getMessage());
                throw new AceException(
                        "Malformed KDF in OSCORE security context");
            }
        }

        CBORObject msC = osc.get(Constants.OS_MS);
        if (msC == null || !msC.getType().equals(CBORType.ByteString)) {
            LOGGER.info("Missing or invalid parameter: 'master secret',"
                    + " must be byte-array");
            throw new AceException("malformed or missing master secret"
                    + " in OSCORE security context");
        }
        this.ms = msC.GetByteString();
        
        CBORObject rpl = osc.get(Constants.OS_RPL);
        if (rpl != null) {
            if (!rpl.CanFitInInt32()) {
                LOGGER.info("Invalid parameter: 'replay window size',"
                        + " must be 32-bit integer");
                throw new AceException("malformed replay window size"
                        + " in OSCORE security context");
            }
            this.replaySize = rpl.AsInt32();
        }

        CBORObject saltC = osc.get(Constants.OS_SALT);
        if (saltC != null) {
            if (!saltC.getType().equals(CBORType.ByteString)) {
                LOGGER.info("Invalid parameter: 'master salt',"
                        + " must be byte-array");
                throw new AceException("malformed master salt"
                        + " in OSCORE security context");
            }
            this.salt = saltC.GetByteString();
        }

        CBORObject serverIdC = osc.get(Constants.OS_SERVERID);
        if (serverIdC == null 
                || !serverIdC.getType().equals(CBORType.ByteString)) {
            LOGGER.info("Missing or invalid parameter: 'serverId',"
                    + " must be byte-array");
            throw new AceException("malformed or missing server id"
                    + " in OSCORE security context");
        }
        this.serverId = serverIdC.GetByteString();
    }
    
    /**
     * @param isClient
     * @param n1  the client's nonce
     * @param n2  the server's nonce
     * @return  an OSCORE context based on this object 
     * @throws OSException 
     */
    public OSCoreCtx getContext(boolean isClient, byte[] n1, byte[] n2) throws OSException {
        byte[] senderId;
        byte[] recipientId;
        byte[] contextId = new byte[n1.length+n2.length];
        System.arraycopy(n1, 0, contextId, 0, n1.length);
        System.arraycopy(n2, 0, contextId, n1.length, n2.length);
        if (isClient) {
            senderId = this.clientId;
            recipientId = this.serverId;
        } else {
            senderId = this.serverId;
            recipientId = this.clientId;
        }
        return new OSCoreCtx(this.ms, isClient, this.alg, senderId, 
                recipientId, this.hkdf, this.replaySize, this.salt, contextId);
    }
    
    /**
     * @return  the client identifier
     */
    public byte[] getClientId() {
        return this.clientId;
    }

}