/*******************************************************************************
 * Copyright (c) 2016, SICS Swedish ICT AB
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
package se.sics.ace;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.Request;

import com.upokecenter.cbor.CBORException;
import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;

import se.sics.ace.cwt.CWT;

/**
 * A CoAP request implementing the Message interface for the ACE library.
 * 
 * @author Ludwig Seitz
 *
 */
public class CoAPRequest extends Request implements Message {

    /**
     * The parameters in the payload of this message as a Map for convenience
     */
    private Map<String, CBORObject> parameters;
    
    /**
     * Constructor 
     * @param code  the RESTful action code
     * @param parameters  the request parameters
     */
    public CoAPRequest(Code code, Map<String, CBORObject> parameters) {
        super(code);
        this.parameters = new HashMap<>();
        this.parameters.putAll(parameters);
        CBORObject map = CBORObject.NewMap();
        for (String key : this.parameters.keySet()) {
            short i = Constants.getAbbrev(key);
            if (i != -1) {
                map.Add(CBORObject.FromObject(i), this.parameters.get(key));
            } else { //This claim/parameter has no abbreviation
                map.Add(CBORObject.FromObject(key), this.parameters.get(key));
            }
        }
        super.setPayload(map.EncodeToBytes());
    }

    @Override
    public byte[] getRawPayload() {
        return super.getPayload();
    }

    @Override
    public String getSenderId() {
        Principal p = super.getSenderIdentity();
        if (p==null) {
            return null;
        }
        return p.toString();
    }

    @Override
    public Set<String> getParameterNames() {
        if (this.parameters != null) {
            return this.parameters.keySet();
        }
        return null;
    }

    @Override
    public CBORObject getParameter(String name) {
        if (this.parameters != null) {
            return this.parameters.get(name);
        }
        return null;
    }

    @Override
    public Map<String, CBORObject> getParameters() {
        if (this.parameters != null) {
            Map<String, CBORObject> map = new HashMap<>();
            map.putAll(this.parameters);
            return map;
        }
        return null;
    }

    @Override
    public Message successReply(int code, CBORObject payload) {
        ResponseCode coapCode = null;
        switch (code) {
        case Message.CREATED :
            coapCode = ResponseCode.CREATED;
            break;
        default:
            coapCode = ResponseCode._UNKNOWN_SUCCESS_CODE;
            break;
        }
        CoAPResponse res = new CoAPResponse(coapCode, payload);
        res.setDestination(super.getSource());
        res.setDestinationPort(super.getSourcePort());
        return res;
    }

    @Override
    public Message failReply(int failureReason, CBORObject payload) {
        ResponseCode coapCode = null;
        switch (failureReason) {
        case Message.FAIL_UNAUTHORIZED :
            coapCode = ResponseCode.UNAUTHORIZED;
            break;
        case Message.FAIL_BAD_REQUEST :
            coapCode = ResponseCode.BAD_REQUEST;
            break;
        case Message.FAIL_FORBIDDEN :
            coapCode = ResponseCode.FORBIDDEN;
            break;
        case Message.FAIL_INTERNAL_SERVER_ERROR :
            coapCode = ResponseCode.INTERNAL_SERVER_ERROR;
            break;
        case Message.FAIL_NOT_IMPLEMENTED :
            coapCode = ResponseCode.NOT_IMPLEMENTED;
            break; 
        default :
        }
        CoAPResponse res = new CoAPResponse(coapCode, payload);
        res.setDestination(super.getSource());
        res.setDestinationPort(super.getSourcePort());
        return res;
    }
    
    /**
     * Create a CoAPRequest from a Californium <code>Request</code>.
     * 
     * @param req  the Californium Request
     * @return  the ACE CoAP request
     * @throws AceException 
     */
    public static CoAPRequest getInstance(Request req) throws AceException {
        Map<String, CBORObject> parameters = null;
        CBORObject cborPayload = null;
        try {
            cborPayload = CBORObject.DecodeFromBytes(req.getPayload());
        } catch (CBORException ex) {
            throw new AceException(ex.getMessage());
        }
        if (cborPayload == null || !cborPayload.getType().equals(CBORType.Map)) {
            throw new AceException("Payload is empty or not encoded as CBOR Map");
        }
        parameters = CWT.parseClaims(cborPayload);
        return new CoAPRequest(req.getCode(), parameters);
    }

    @Override
    public int getMessageCode() {
        return getCode().value;
    }

}