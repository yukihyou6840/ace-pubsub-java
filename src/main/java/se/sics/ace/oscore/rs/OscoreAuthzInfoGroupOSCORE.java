/*******************************************************************************
 * Copyright (c) 2019, RISE AB
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR 
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT 
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY 
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/
package se.sics.ace.oscore.rs;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.oscore.OSCoreCtx;
import org.eclipse.californium.oscore.OSException;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;

import se.sics.ace.AceException;
import se.sics.ace.Constants;
import se.sics.ace.Message;
import se.sics.ace.TimeProvider;
import se.sics.ace.coap.rs.oscoreProfile.OscoreCtxDbSingleton;
import se.sics.ace.coap.rs.oscoreProfile.OscoreSecurityContext;
import se.sics.ace.cwt.CwtCryptoCtx;
import se.sics.ace.oscore.GroupInfo;
import se.sics.ace.rs.AudienceValidator;
import se.sics.ace.rs.AuthzInfo;
import se.sics.ace.rs.IntrospectionHandler;
import se.sics.ace.rs.ScopeValidator;
import se.sics.ace.rs.TokenRepository;


/**
 * This class implements the /authz_info endpoint at the RS that receives
 * access tokens, verifies if they are valid and then stores them.
 * 
 * Note this implementation requires the following claims in a CWT:
 * iss, sub, scope, aud.
 * 
 * @author Ludwig Seitz and Marco Tiloca
 *
 */
public class OscoreAuthzInfoGroupOSCORE extends AuthzInfo {
	
    /**
     * The logger
     */
    private static final Logger LOGGER 
        = Logger.getLogger(OscoreAuthzInfoGroupOSCORE.class.getName());

    /**
     * Temporary storage for the CNF claim
     */
    private CBORObject cnf;
    
    /**
	 * Handles audience validation
	 */
	private GroupOSCOREJoinValidator audience;
    
    /**
     * OSCORE groups active under the Group Manager
     */

	private Map<String, GroupInfo> activeGroups;
	
	private final String rootGroupMembershipResource = "ace-group";
	
	/**
	 * Constructor.
	 * 
	 * @param tr  a token repository
	 * @param issuers  the list of acceptable issuer of access tokens
	 * @param time  the time provider
	 * @param intro  the introspection handler (can be null)
	 * @param audience  the audience validator
	 * @param ctx  the crypto context to use with the As
	 * @param tokenFile  the file where to save tokens when persisting
     * @param scopeValidator  the application specific scope validator 
	 * @param checkCnonce  true if this RS uses cnonces for freshness validation
	 * @throws IOException 
	 * @throws AceException 
	 */
	public OscoreAuthzInfoGroupOSCORE(List<String> issuers, 
			TimeProvider time, IntrospectionHandler intro, 
			AudienceValidator audience, CwtCryptoCtx ctx, String tokenFile,
			ScopeValidator scopeValidator, boolean checkCnonce) 
			        throws AceException, IOException {
		super(issuers, time, intro, audience, ctx, tokenFile, 
		        scopeValidator, checkCnonce);
		
		this.audience = (GroupOSCOREJoinValidator) audience;
		
	}

	@Override
	public synchronized Message processMessage(Message msg) {
	    LOGGER.log(Level.INFO, "received message: " + msg);
	    CBORObject token = null;
	    CBORObject cbor = null;
	    boolean provideSignInfo = false;
	    boolean invalid = false;
	    
        try {
            cbor = CBORObject.DecodeFromBytes(msg.getRawPayload());
        } catch (Exception e) {
            LOGGER.info("Invalid payload at authz-info: " + e.getMessage());
            CBORObject map = CBORObject.NewMap();
            map.Add(Constants.ERROR, Constants.INVALID_REQUEST);
            map.Add(Constants.ERROR_DESCRIPTION, "Invalid payload");
            return msg.failReply(Message.FAIL_BAD_REQUEST, map);
        }
        if (!cbor.getType().equals(CBORType.Map)) {
            LOGGER.info("Invalid payload at authz-info: not a cbor map");
            CBORObject map = CBORObject.NewMap();
            map.Add(Constants.ERROR, Constants.INVALID_REQUEST);
            map.Add(Constants.ERROR_DESCRIPTION, 
                    "Payload to authz-info must be a CBOR map");
            return msg.failReply(Message.FAIL_BAD_REQUEST, map);
        }
        
        token = cbor.get(CBORObject.FromObject(Constants.ACCESS_TOKEN));
        if (token == null) {
            LOGGER.info("Missing manadory paramter 'access_token'");
            CBORObject map = CBORObject.NewMap();
            map.Add(Constants.ERROR, Constants.INVALID_REQUEST);
            map.Add(Constants.ERROR_DESCRIPTION, 
                    "Missing mandatory parameter 'access_token'");
            return msg.failReply(Message.FAIL_BAD_REQUEST, map);
        }
        
        if (cbor.ContainsKey(CBORObject.FromObject(Constants.SIGN_INFO))) {
    		if (cbor.get(CBORObject.FromObject(Constants.SIGN_INFO)).equals(CBORObject.Null)) {
    			provideSignInfo = true;
    		}
    		else invalid = true;
    	}
    	
        if (invalid) {
            LOGGER.info("Invalid format for 'sign_info'");
            CBORObject map = CBORObject.NewMap();
            map.Add(Constants.ERROR, Constants.INVALID_REQUEST);
            map.Add(Constants.ERROR_DESCRIPTION, 
                    "Invalid format for 'sign_info'");
            return msg.failReply(Message.FAIL_BAD_REQUEST, map);
        }
        
        Message reply = super.processToken(token, msg);
        if (reply.getMessageCode() != Message.CREATED) {
            return reply;
        }
        
        if (this.cnf == null) {//Should never happen, caught in TokenRepository
            LOGGER.info("Missing required parameter 'cnf'");
            CBORObject map = CBORObject.NewMap();
            map.Add(Constants.ERROR, Constants.INVALID_REQUEST);
            return msg.failReply(Message.FAIL_BAD_REQUEST, map); 
        }

        CBORObject nonce = cbor.get(CBORObject.FromObject(Constants.NONCE1));
        if (nonce == null || !nonce.getType().equals(CBORType.ByteString)) {
            LOGGER.info("Missing or invalid parameter type for:"
                    + "'nonce1', must be present and byte-string");
            CBORObject map = CBORObject.NewMap();
            map.Add(Constants.ERROR, Constants.INVALID_REQUEST);
            map.Add(Constants.ERROR_DESCRIPTION, 
                    "Malformed or missing parameter 'nonce1'");
            return msg.failReply(Message.FAIL_BAD_REQUEST, map); 
        }
        byte[] n1 = nonce.GetByteString();
        byte[] n2 = new byte[8];
        new SecureRandom().nextBytes(n2);
                
        OscoreSecurityContext osc;
        try {
            osc = new OscoreSecurityContext(this.cnf);
        } catch (AceException e) {
            CBORObject map = CBORObject.NewMap();
            map.Add(Constants.ERROR, Constants.INVALID_REQUEST);
            map.Add(Constants.ERROR_DESCRIPTION, e.getMessage());
            return msg.failReply(Message.FAIL_BAD_REQUEST, map); 
        }
            
        OSCoreCtx ctx;
        try {
            ctx = osc.getContext(false, n1, n2);
            OscoreCtxDbSingleton.getInstance().addContext(ctx);
        } catch (OSException e) {
            LOGGER.info("Error while creating OSCORE context: " 
                    + e.getMessage());
            CBORObject map = CBORObject.NewMap();
            map.Add(Constants.ERROR, Constants.INVALID_REQUEST);
            map.Add(Constants.ERROR_DESCRIPTION, 
                    "Error while creating OSCORE security context: "
                    + e.getMessage());
            return msg.failReply(Message.FAIL_BAD_REQUEST, map);
        }
        
        CBORObject payload = CBORObject.NewMap();
        payload.Add(Constants.NONCE2, n2);
        
        
        
        //Return the cti or the local identifier assigned to the token
	    CBORObject responseMap = CBORObject.DecodeFromBytes(reply.getRawPayload());
	    CBORObject cti = responseMap.get(CBORObject.FromObject(Constants.CTI));
	    payload.Add(Constants.CTI, cti);
	    	
    	boolean error = true;
    	
	    String ctiStr = Base64.getEncoder().encodeToString(cti.GetByteString());
	    Map<Short, CBORObject> claims = TokenRepository.getInstance().getClaims(ctiStr);
    	
    	// Check that audience and scope are consistent with the access to a join resource.
	    // Consistency checks have been already performed when processing the Token upon posting
	    
    	CBORObject scope = claims.get(Constants.SCOPE);
    	
    	if (scope.getType().equals(CBORType.ByteString)) {
    	
    		CBORObject aud = claims.get(Constants.AUD);
    		
    		Set<String> myGMAudiences = this.audience.getAllGMAudiences();
    		Set<String> myJoinResources = this.audience.getAllJoinResources();
    		
    		ArrayList<String> auds = new ArrayList<>();
    	    if (aud.getType().equals(CBORType.Array)) {
    	        for (int i=0; i<aud.size(); i++) {
    	            if (aud.get(i).getType().equals(CBORType.TextString)) {
    	                auds.add(aud.get(i).AsString());
    	            } //XXX: silently skip aud entries that are not text strings
    	        }
    	    } else if (aud.getType().equals(CBORType.TextString)) {
    	        auds.add(aud.AsString());
    	    }
    		
    		byte[] rawScope = scope.GetByteString();
    		CBORObject cborScope = CBORObject.DecodeFromBytes(rawScope);
    		Set<String> groupNames = new HashSet<>();
    		
    		// Check that the audience is in fact a Group Manager
    		for (String foo : auds) {
    			if (myGMAudiences.contains(foo)) {
    				error = false;
    	    		break;
    	    	}
    	    }
    		
      	  	for (int entryIndex = 0; entryIndex < cborScope.size(); entryIndex++)
      	  		groupNames.add(cborScope.get(entryIndex).get(0).AsString());
    		
    		// Check that all the group names in scope refer to group-membership resources
    		if (error == false) {
    			for (String groupName : groupNames) {
    				if (myJoinResources.contains(rootGroupMembershipResource + "/" + groupName) == false) {
    					error = true;
    					break;
    				}
    			}
    		}
    		
    		if (error == true) {
                LOGGER.info("The audience must be a Group Manager; group name must point at group-membership resources of that Group Manager");
                CBORObject map = CBORObject.NewMap();
                map.Add(Constants.ERROR, Constants.INVALID_REQUEST);
                return msg.failReply(Message.FAIL_BAD_REQUEST, map); 
            }
    		
        	// Add the nonce for PoP of the Client's private key in the Join Request
            byte[] rsnonce = new byte[8];
            new SecureRandom().nextBytes(rsnonce);
            payload.Add(Constants.KDCCHALLENGE, rsnonce);
            
    	    CBORObject sid = responseMap.get(CBORObject.FromObject(Constants.SUB));
    	    
    	    if (sid == null) { // This should never happen, as handled in TokenRepository.
                LOGGER.info("Missing Sender ID after valid Access Token Posting");
                CBORObject map = CBORObject.NewMap();
                map.Add(Constants.ERROR, Constants.INVALID_REQUEST);
                return msg.failReply(Message.FAIL_BAD_REQUEST, map); 
            }
    	    
    	    // TODO: REMOVE DEBUG PRINT
    	    // System.out.println("AuthzInfoGroupOSCORE " + sid);
    	    
    	    // TODO: REMOVE DEBUG PRINT
    	    // System.out.println("AuthzInfoGroupOSCORE " + sid.AsString());
    	    // System.out.println("AuthzInfoGroupOSCORE " + Base64.getEncoder().encodeToString(rsnonce));
    	    
    	    // Add to the Token Repository an entry (sid, rsnonce)
    	    TokenRepository.getInstance().setRsnonce(sid.AsString(), Base64.getEncoder().encodeToString(rsnonce));
    	    
	    	if (provideSignInfo) {
	    	    
	    		CBORObject signInfo = CBORObject.NewArray();
	    	
				for (String groupName : groupNames) {
					
		        	// Retrieve the entry for the target group, using the name of the OSCORE group
		        	GroupInfo myGroup = this.activeGroups.get(groupName);
					
					CBORObject signInfoEntry = CBORObject.NewArray();
					
					signInfoEntry.Add(CBORObject.FromObject(groupName)); // 'id' element
									
					signInfoEntry.Add(myGroup.getCsAlg().AsCBOR()); // 'sign_alg' element
			    	
			    	CBORObject arrayElem = myGroup.getCsParams(); // 'sign_parameters' element
			    	if (arrayElem == null)
			    		signInfoEntry.Add(CBORObject.Null);
			    	else
			    		signInfoEntry.Add(arrayElem);
			    	
			    	arrayElem = myGroup.getCsKeyParams(); // 'sign_key_parameters' element
			    	if (arrayElem == null)
			    		signInfoEntry.Add(CBORObject.Null);
			    	else
			    		signInfoEntry.Add(arrayElem);
			    	
			    	signInfoEntry.Add(myGroup.getCsKeyEnc()); // 'pub_key_enc' element

			    	
				    signInfo.Add(signInfoEntry);
				    
				}

		    	payload.Add(Constants.SIGN_INFO, signInfo);
		    
	    	}
    		
    	}
        
        LOGGER.info("Successfully processed OSCORE token");
        return msg.successReply(reply.getMessageCode(), payload);
	}

	/**
	 * @param activeGroups
	 */
	public synchronized void setActiveGroups(Map<String, GroupInfo> activeGroups) {
		this.activeGroups = activeGroups;
	}
	
	@Override
	protected synchronized void processOther(Map<Short, CBORObject> claims) {
	    this.cnf = claims.get(Constants.CNF);
	}

    @Override
    public void close() throws AceException {
       super.close();
        
    }	
}
