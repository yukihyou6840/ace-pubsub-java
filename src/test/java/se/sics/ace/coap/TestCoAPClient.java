package se.sics.ace.coap;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;
import org.junit.AfterClass;
import org.junit.Test;

import com.upokecenter.cbor.CBORObject;

import COSE.AlgorithmID;
import COSE.OneKey;
import se.sics.ace.AceException;
import se.sics.ace.Constants;
import se.sics.ace.ReferenceToken;
import se.sics.ace.as.DBConnector;
import se.sics.ace.as.Token;

/**
 * Test the coap classes, you have to run TestCoAPServer first to start the server.
 * 
 * @author Ludwig Seitz
 *
 */
public class TestCoAPClient {
    
    static byte[] key256 = {'a', 'b', 'c', 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,28, 29, 30, 31, 32};
    static String aKey = "piJYICg7PY0o/6Wf5ctUBBKnUPqN+jT22mm82mhADWecE0foI1ghAKQ7qn7SL/Jpm6YspJmTWbFG8GWpXE5GAXzSXrialK0pAyYBAiFYIBLW6MTSj4MRClfSUzc8rVLwG8RH5Ak1QfZDs4XhecEQIAE=";
    
    /**
     * Deletes the test DB after the tests
     * 
     * @throws SQLException 
     * @throws AceException 
     * @throws IOException 
     */
    @AfterClass
    public static void tearDown() throws SQLException, AceException, IOException {
        String dbPwd = null;
        BufferedReader br = new BufferedReader(new FileReader("db.pwd"));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            dbPwd = sb.toString().replace(
                    System.getProperty("line.separator"), "");     
        } finally {
            br.close();
        }
        Properties connectionProps = new Properties();
        connectionProps.put("user", "root");
        connectionProps.put("password", dbPwd);
        Connection rootConn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306", connectionProps);

        String dropDB = "DROP DATABASE IF EXISTS " + DBConnector.dbName + ";";
        String dropUser = "DROP USER 'aceUser'@'localhost';";
        Statement stmt = rootConn.createStatement();
        stmt.execute(dropDB);
        stmt.execute(dropUser);    
        stmt.close();
        rootConn.close();   
    }
    
    /**
     * Test CoapToken using PSK
     * 
     * @throws Exception 
     */
    @Test
    public void testCoapToken() throws Exception {
        OneKey asymmetricKey = OneKey.generateKey(AlgorithmID.ECDSA_256);
        DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder(
                new InetSocketAddress(0));
        builder.setPskStore(new StaticPskStore("clientA", key256));
        builder.setIdentity(asymmetricKey.AsPrivateKey(), 
                asymmetricKey.AsPublicKey());
        builder.setSupportedCipherSuites(new CipherSuite[]{
                CipherSuite.TLS_PSK_WITH_AES_128_CCM_8});
        DTLSConnector dtlsConnector = new DTLSConnector(builder.build());
        dtlsConnector.start();
        CoapEndpoint e = new CoapEndpoint(dtlsConnector, NetworkConfig.getStandard());
        CoapClient client = new CoapClient("coaps://localhost/token");
        client.setEndpoint(e);        

        Map<String, CBORObject> params = new HashMap<>();
        params.put("grant_type", Token.clientCredentialsStr);
        params.put("scope", 
                CBORObject.FromObject("r_temp rw_config foobar"));
        params.put("aud", CBORObject.FromObject("rs1"));
        CoapResponse response = client.post(
                Constants.abbreviate(params).EncodeToBytes(), 
                MediaTypeRegistry.APPLICATION_CBOR);    
        CBORObject res = CBORObject.DecodeFromBytes(response.getPayload());
        Map<String, CBORObject> map = Constants.unabbreviate(res);
        System.out.println(map);
        assert(map.containsKey("access_token"));
        assert(map.containsKey("profile"));
        assert(map.get("profile").AsString().equals("coap_oscoap"));
        assert(map.containsKey("cnf"));
        assert(map.containsKey("scope"));
        assert(map.get("scope").AsString().equals("r_temp rw_config"));
    }
    
    /**
     * Test CoapIntrospect using RPK
     * 
     * @throws Exception
     */
    @Test
    public void testCoapIntrospect() throws Exception {
        OneKey key = new OneKey(
                CBORObject.DecodeFromBytes(Base64.getDecoder().decode(aKey)));
        DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder(
                new InetSocketAddress(0));
        //builder.setPskStore(new StaticPskStore("rs1", key256));
        builder.setIdentity(key.AsPrivateKey(), 
                key.AsPublicKey());
        builder.setSupportedCipherSuites(new CipherSuite[]{
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8});
        DTLSConnector dtlsConnector = new DTLSConnector(builder.build());
        dtlsConnector.start();
        CoapEndpoint e = new CoapEndpoint(dtlsConnector, NetworkConfig.getStandard());
        CoapClient client = new CoapClient("coaps://localhost/introspect");
        client.setEndpoint(e);        
       
        ReferenceToken at = new ReferenceToken("token1");
        Map<String, CBORObject> params = new HashMap<>();
        params.put("access_token", at.encode());
        CoapResponse response = client.post(
                Constants.abbreviate(params).EncodeToBytes(), 
                MediaTypeRegistry.APPLICATION_CBOR);
        CBORObject res = CBORObject.DecodeFromBytes(response.getPayload());
        Map<String, CBORObject> map = Constants.unabbreviate(res);
        System.out.println(map);
        assert(map.containsKey("aud"));
        assert(map.get("aud").AsString().equals("actuators"));
        assert(map.containsKey("scope"));
        assert(map.get("scope").AsString().equals("co2"));
        assert(map.containsKey("active"));
        assert(map.get("active").isTrue());
        assert(map.containsKey("cti"));
        assert(map.containsKey("exp"));
        
    }
}
