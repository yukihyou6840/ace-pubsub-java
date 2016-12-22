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
package se.sics.ace.examples;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import com.upokecenter.cbor.CBORObject;

import COSE.CoseException;
import COSE.OneKey;

import se.sics.ace.AceException;
import se.sics.ace.COSEparams;
import se.sics.ace.as.AccessTokenFactory;
import se.sics.ace.as.DBConnector;

/**
 * This class provides SQL database connectivity for the Attribute Authority.
 * 
 * @author Ludwig Seitz
 *
 */
public class SQLConnector implements DBConnector {

	/**
	 * The default user of the database
	 */
	private String defaultUser = "aceUser";
	
	/**
	 * The default password of the default user. 
	 * CAUTION! Only use this for testing, this is very insecure
	 * (but then if you didn't figure that out yourself, I cannot help you
	 * anyway).
	 */
	private String defaultPassword = "password";
	
	/**
	 * The default connection URL for the database. Here we use a 
	 * MySQL database on port 3306.
	 */
	private String defaultDbUrl = "jdbc:mysql://localhost:3306";
	
	/**
	 * A prepared connection.
	 */
	private Connection conn = null;
	
	/**
	 * A prepared INSERT statement to add a new Resource Server.
	 * 
	 * Parameters: rs id, cose encoding, default expiration time, psk, rpk
	 */
	private PreparedStatement insertRS;
	
	/**
     * A prepared DELETE statement to remove a Resource Server
     * 
     * Parameter: rs id.
     */
    private PreparedStatement deleteRS;
    
    /**
     * A prepared SELECT statement to get a set of RS for an audience
     * 
     * Parameter: audience name
     */
    private PreparedStatement selectRS;
    
	/**
	 * A prepared INSERT statement to add a profile supported
	 * by a client or Resource Server
	 * 
	 * Parameters: id, profile name
	 */
	private PreparedStatement insertProfile;
	
	/**
     * A prepared DELETE statement to remove the profiles supported
     * by a client or Resource Server
     * 
     * Parameter: id
     */
    private PreparedStatement deleteProfiles;
	
    /**
     * A prepared SELECT statement to get all profiles for 
     * an audience and a client
     * 
     * Parameters: audience name, client id
     */
    private PreparedStatement selectProfiles;
    
	/**
	 * A prepared INSERT statement to add the key types supported
     * by a client or Resource Server
     * 
     * Parameters: id, key type
	 */
	private PreparedStatement insertKeyType;
	 
	/**
     * A prepared DELETE statement to remove the key types supported
     * by a client or Resource Server
     * 
     * Parameter: id
     */
    private PreparedStatement deleteKeyTypes;
    
    /**
     * A prepared SELECT statement to get a set of key types
     * 
     * Parameters: audience name, client id
     */
    private PreparedStatement selectKeyTypes;
	
	/**
     * A prepared INSERT statement to add the scopes supported
     * by a Resource Server
     * 
     * Parameters: rs id, scope name
     */
    private PreparedStatement insertScope;
    
    /**
     * A prepared DELETE statement to remove the scopes supported
     * by a Resource Server
     * 
     * Parameter: rs id
     */
    private PreparedStatement deleteScopes;   
    
    /**
     * A prepared SELECT statement to get a set of Scopes for a specific RS
     * 
     * Parameter: rs id
     */
    private PreparedStatement selectScopes;
    
    /**
     * A prepared INSERT statement to add an audience a 
     * Resource Server identifies with
     * 
     * Parameter: rs id, audience name
     */
    private PreparedStatement insertAudience;
	
    /**
     * A prepared DELETE statement to remove the audiences
     * a Resource Server identifies with
     * 
     * Parameter: rs id
     */
    private PreparedStatement deleteAudiences;   
    
    /**
     * A prepared SELECT statement to get a set of audiences for an RS
     * 
     * Parameter: rs id
     */
    private PreparedStatement selectAudiences;
    
    /**
     * A prepared INSERT statement to add a token type a 
     * Resource Server supports
     * 
     * Parameters: rs id, token type
     */
    private PreparedStatement insertTokenType;
    
    /**
     * A prepared DELETE statement to remove the token types a
     * a Resource Server supports
     * 
     * Parameter: rs id
     */
    private PreparedStatement deleteTokenTypes;   

    /**
     * A prepared SELECT statement to get a set of token types for an audience
     * 
     * Parameter: audience name
     */
    private PreparedStatement selectTokenTypes;
    
	/**
	 * A prepared INSERT statement to add a new client
	 * 
	 * Parameters: client id, default audience, default scope, psk, rpk
	 */
	private PreparedStatement insertClient;
	
	/**
	 * A prepared DELETE statement to remove a client
	 * 
	 * Parameter: client id
	 */
	private PreparedStatement deleteClient;
	
	/**
	 * A prepared SELECT statement to get the default audience for a client.
	 * 
	 *  Parameter: client id
	 */
	private PreparedStatement selectDefaultAudience;
	
	/**
     * A prepared SELECT statement to get the default scope for a client.
     * 
     *  Parameter: client id
     */
    private PreparedStatement selectDefaultScope;

    
    /**
     * A prepared INSERT statement to add a new supported cose configuration
     * for protecting CWTs
     * 
     * Parameters: rs id, cose config
     */
    private PreparedStatement insertCose;
    
    /**
     * A prepared DELETE statement to remove a cose configuration
     * 
     * Parameter: rs id
     */
    private PreparedStatement deleteCose;
    
	/**
	 * A prepared SELECT statement to get the COSE configurations for
	 * an audience.
	 * 
	 * Parameter: audience name
	 */
	private PreparedStatement selectCOSE;
	
	/**
     * A prepared SELECT statement to get the default expiration time for
     *     a RS
     *     
     * Parameter: audience name
     */
    private PreparedStatement selectExpiration;
	
    /**
     * A prepared SELECT statement to get a the pre-shared keys for
     *     an audience
     *     
     * Parameter: audience name
     */
    private PreparedStatement selectRsPSK;
    
    /**
     * A prepared SELECT statement to get the public keys of an audience.
     * 
     * Parameter: audience name
     */
    private PreparedStatement selectRsRPK;
    
    /**
     * A prepared SELECT statement to get a the pre-shared key for
     *     an client.
     * 
     * Parameter: client id
     */
    private PreparedStatement selectCPSK;
    
    /**
     * A prepared SELECT statement to get the public key of a client.
     * 
     * Parameter: client id
     */
    private PreparedStatement selectCRPK;
    
    /**
     * A prepared SELECT statement to fetch token ids and their
     * expiration time form the claims table.
     */
    private PreparedStatement selectExpirationTime;
    
    /**
     * A prepared INSERT statement to add a claim of a token 
     * to the Claims table.
     * 
     * Parameters: token cid, claim name, claim value
     */
    private PreparedStatement insertClaim;
    
    /**
     * A prepared DELETE statement to remove the claims of a token 
     * from the Claims table.
     * 
     * Parameters: token cid
     */
    private PreparedStatement deleteClaims;
    
    /**
     * A prepared SELECT statement to select the claims of a token from
     * the Claims table.
     * 
     * Parameter: token cid
     */
    private PreparedStatement selectClaims;
    
    /**
     * A prepared SELECT statement to select the cti counter value from the 
     * cti counter table.
     */
    private PreparedStatement selectCtiCtr;
    
    /**
     * A prepared UPDATE statement to update the saved cti counter value in the
     * cti counter table.
     */
    private PreparedStatement updateCtiCtr;

	/**
	 * Create a new database connector either from given values or the 
	 * defaults.
	 * 
	 * @param dbUrl  the database URL, if null the default will be used
	 * @param user   the database user, if null the default will be used
	 * @param pwd    the database user's password, if null the default 
	 * 				 will be used
	 * @throws SQLException 
	 */
	public SQLConnector(String dbUrl, String user, String pwd) 
			throws SQLException {
		if (dbUrl != null) {
			this.defaultDbUrl = dbUrl;
		}
		if (user != null) {
			this.defaultUser = user;
		}
		if (pwd != null) {
			this.defaultPassword = pwd;
		}

		Properties connectionProps = new Properties();      
		connectionProps.put("user", this.defaultUser);
		connectionProps.put("password", this.defaultPassword);
		this.conn = DriverManager.getConnection(this.defaultDbUrl, 
		        connectionProps);
	        
		this.insertRS = this.conn.prepareStatement("INSERT INTO "
		        + DBConnector.dbName + "." + DBConnector.rsTable
		        + " VALUES (?,?,?,?);");
		
		this.deleteRS = this.conn.prepareStatement("DELETE FROM "
                + DBConnector.dbName + "." + DBConnector.rsTable
                + " WHERE " + DBConnector.rsIdColumn + "=?;");
		
		this.selectRS = this.conn.prepareStatement("SELECT "
                + DBConnector.rsIdColumn
                + " FROM " + DBConnector.dbName + "." 
                + DBConnector.audiencesTable
                + " WHERE " + DBConnector.audColumn + "=? ORDER BY "
                        + DBConnector.rsIdColumn + ";");
		        
		this.insertProfile = this.conn.prepareStatement("INSERT INTO "
		        + DBConnector.dbName + "." + DBConnector.profilesTable
		        + " VALUES (?,?)");
		
		this.deleteProfiles = this.conn.prepareStatement("DELETE FROM "
                + DBConnector.dbName + "." + DBConnector.profilesTable
                + " WHERE " + DBConnector.idColumn + "=?;");
		
		this.selectProfiles = this.conn.prepareStatement("SELECT * FROM " 
		        + DBConnector.dbName + "." + DBConnector.profilesTable
                + " WHERE " + DBConnector.idColumn + " IN (SELECT " 
                    + DBConnector.rsIdColumn + " FROM " 
                    + DBConnector.dbName + "." + DBConnector.audiencesTable 
                    + " WHERE " + DBConnector.audColumn
                    + "=?) UNION SELECT * FROM " 
                    + DBConnector.dbName + "." + DBConnector.profilesTable
                    + " WHERE " + DBConnector.idColumn + "=? ORDER BY "
                    + DBConnector.idColumn + ";"); 
			
		this.insertKeyType = this.conn.prepareStatement("INSERT INTO "
                + DBConnector.dbName + "." + DBConnector.keyTypesTable
                + " VALUES (?,?)");
		
		this.deleteKeyTypes = this.conn.prepareStatement("DELETE FROM "
	                + DBConnector.dbName + "." + DBConnector.keyTypesTable
	                + " WHERE " + DBConnector.idColumn + "=?;");
		
		this.selectKeyTypes =  this.conn.prepareStatement("SELECT * FROM " 
                + DBConnector.dbName + "." + DBConnector.keyTypesTable
                + " WHERE " + DBConnector.idColumn + " IN (SELECT " 
                    + DBConnector.rsIdColumn + " FROM " 
                    + DBConnector.dbName + "." + DBConnector.audiencesTable
                    + " WHERE " + DBConnector.audColumn + "=?)"
                    + " UNION SELECT * FROM " + DBConnector.dbName + "." 
                    + DBConnector.keyTypesTable + " WHERE " 
                    + DBConnector.idColumn + "=? ORDER BY "
                    + DBConnector.idColumn + ";");             
		          
		this.insertScope = this.conn.prepareStatement("INSERT INTO "
                + DBConnector.dbName + "." + DBConnector.scopesTable
                + " VALUES (?,?)");
		
		this.deleteScopes = this.conn.prepareStatement("DELETE FROM "
                + DBConnector.dbName + "." + DBConnector.scopesTable
                + " WHERE " + DBConnector.rsIdColumn + "=?;");

		this.selectScopes = this.conn.prepareStatement("SELECT * FROM " 
                + DBConnector.dbName + "." + DBConnector.scopesTable
                + " WHERE " + DBConnector.rsIdColumn + " IN (SELECT " 
                    + DBConnector.rsIdColumn + " FROM " 
                    + DBConnector.dbName + "." + DBConnector.audiencesTable
                    + " WHERE " + DBConnector.audColumn + "=?) ORDER BY "
                    + DBConnector.rsIdColumn + ";");          
		
		this.insertAudience = this.conn.prepareStatement("INSERT INTO "
                + DBConnector.dbName + "." + DBConnector.audiencesTable
                + " VALUES (?,?)");
		
		this.deleteAudiences = this.conn.prepareStatement("DELETE FROM "
	                + DBConnector.dbName + "." + DBConnector.audiencesTable
	                + " WHERE " + DBConnector.rsIdColumn + "=?;");
		
		this.selectAudiences = this.conn.prepareStatement("SELECT " 
		        + DBConnector.audColumn + " FROM "
		        + DBConnector.dbName + "." + DBConnector.audiencesTable
                + " WHERE " + DBConnector.rsIdColumn + "=? ORDER BY "
                + DBConnector.audColumn + ";");          
		
		this.insertTokenType = this.conn.prepareStatement("INSERT INTO "
                + DBConnector.dbName + "." + DBConnector.tokenTypesTable
                + " VALUES (?,?)");
		
		this.deleteTokenTypes = this.conn.prepareStatement("DELETE FROM "
                + DBConnector.dbName + "." + DBConnector.tokenTypesTable
                + " WHERE " + DBConnector.rsIdColumn + "=?;");
		
		this.selectTokenTypes = this.conn.prepareStatement("SELECT * FROM " 
                + DBConnector.dbName + "." + DBConnector.tokenTypesTable
                + " WHERE " + DBConnector.rsIdColumn + " IN (SELECT " 
                    + DBConnector.rsIdColumn + " FROM " 
                    + DBConnector.dbName + "." + DBConnector.audiencesTable 
                    + " WHERE " + DBConnector.audColumn + "=?) ORDER BY "
                    + DBConnector.rsIdColumn + ";");
		
		this.insertClient = this.conn.prepareStatement("INSERT INTO "
                + DBConnector.dbName + "." + DBConnector.cTable
                + " VALUES (?,?,?,?,?)");
	
		this.deleteClient = this.conn.prepareStatement("DELETE FROM "
                + DBConnector.dbName + "." + DBConnector.cTable
                + " WHERE " + DBConnector.clientIdColumn + "=?;");
		
		this.selectDefaultAudience = this.conn.prepareStatement("SELECT " 
		        + DBConnector.defaultAud + " FROM " 
                + DBConnector.dbName + "." + DBConnector.cTable
                + " WHERE " + DBConnector.clientIdColumn + "=?;");
		  
		this.selectDefaultScope = this.conn.prepareStatement("SELECT " 
	                + DBConnector.defaultScope + " FROM " 
	                + DBConnector.dbName + "." + DBConnector.cTable
	                + " WHERE " + DBConnector.clientIdColumn + "=?;");
		
		this.insertCose = this.conn.prepareStatement("INSERT INTO "
                + DBConnector.dbName + "." + DBConnector.coseTable
                + " VALUES (?,?)");
		
		this.deleteCose = this.conn.prepareStatement("DELETE FROM "
                + DBConnector.dbName + "." + DBConnector.coseTable
                + " WHERE " + DBConnector.rsIdColumn + "=?;");
		
		this.selectCOSE = this.conn.prepareStatement("SELECT * "
                + " FROM " + DBConnector.dbName + "." + DBConnector.coseTable
                + " WHERE " + DBConnector.rsIdColumn + " IN (SELECT " 
                    + DBConnector.rsIdColumn + " FROM " 
                    + DBConnector.dbName + "." + DBConnector.audiencesTable 
                    + " WHERE " + DBConnector.audColumn + "=?) ORDER BY "
                    + DBConnector.rsIdColumn + ";");
	      
		this.selectExpiration = this.conn.prepareStatement("SELECT "
	                + DBConnector.expColumn 
	                + " FROM " + DBConnector.dbName + "." + DBConnector.rsTable
	                + " WHERE " + DBConnector.rsIdColumn + "=?;");
		        
		this.selectRsPSK = this.conn.prepareStatement("SELECT "
		        + DBConnector.pskColumn
		        + " FROM " + DBConnector.dbName + "." + DBConnector.rsTable
		        + " WHERE " + DBConnector.rsIdColumn + " IN (SELECT " 
		            + DBConnector.rsIdColumn + " FROM " 
		            + DBConnector.dbName + "." + DBConnector.audiencesTable
		            + " WHERE " + DBConnector.audColumn + "=?);");

		this.selectRsRPK = this.conn.prepareStatement("SELECT " 
		        + DBConnector.rpkColumn
		        + " FROM " + DBConnector.dbName + "." + DBConnector.rsTable
		        + " WHERE " + DBConnector.rsIdColumn + " IN (SELECT " 
		            + DBConnector.rsIdColumn + " FROM " 
		            + DBConnector.dbName + "." + DBConnector.audiencesTable 
		            + " WHERE " + DBConnector.audColumn + "=?);");

		this.selectCPSK = this.conn.prepareStatement("SELECT "
		        + DBConnector.pskColumn
		        + " FROM " + DBConnector.dbName + "." + DBConnector.cTable
		        + " WHERE " + DBConnector.clientIdColumn + "=?;");

		this.selectCRPK = this.conn.prepareStatement("SELECT " 
		        + DBConnector.rpkColumn
		        + " FROM " + DBConnector.dbName + "." + DBConnector.cTable
		        + " WHERE "  + DBConnector.clientIdColumn + "=?;");

		this.selectExpirationTime = this.conn.prepareStatement("SELECT "
		        + DBConnector.cidColumn + "," + DBConnector.claimValueColumn
		        + " FROM "  + DBConnector.dbName + "." 
		        + DBConnector.claimsTable
		        + " WHERE " + DBConnector.claimNameColumn + "='exp';");
		        
		this.insertClaim = this.conn.prepareStatement("INSERT INTO "
                + DBConnector.dbName + "." + DBConnector.claimsTable
                + " VALUES (?,?,?)");
        
        this.deleteClaims = this.conn.prepareStatement("DELETE FROM "
                + DBConnector.dbName + "." + DBConnector.claimsTable
                + " WHERE " + DBConnector.cidColumn + "=?;");
    
        this.selectClaims = this.conn.prepareStatement("SELECT "
                + DBConnector.claimNameColumn + ","
                + DBConnector.claimValueColumn + " FROM " 
                + DBConnector.dbName + "." + DBConnector.claimsTable
                + " WHERE " + DBConnector.cidColumn + "=?;");  	
        
        this.selectCtiCtr = this.conn.prepareStatement("SELECT "
                + DBConnector.ctiCounterColumn + " FROM "
                + DBConnector.dbName + "." + DBConnector.ctiCounterTable 
                + ";");
        
        this.updateCtiCtr = this.conn.prepareStatement("UPDATE "
                + DBConnector.dbName + "." + DBConnector.ctiCounterTable
                + " SET " + DBConnector.ctiCounterColumn + "=?;");
	}
	
	/**
	 * Create the necessary database and tables. Requires the
	 * root user password.
	 * 
	 * @param rootPwd  the root user password
	 * @throws AceException 
	 */
	@Override
	public synchronized void init(String rootPwd) throws AceException {
				
		String createDB = "CREATE DATABASE IF NOT EXISTS " + DBConnector.dbName
		        + " CHARACTER SET utf8 COLLATE utf8_bin;";
	
		//rs id, cose encoding, default expiration time, psk, rpk
		String createRs = "CREATE TABLE IF NOT EXISTS " + DBConnector.dbName 
		        + "." + DBConnector.rsTable + "(" 
		        + DBConnector.rsIdColumn + " varchar(255) NOT NULL, " 
                + DBConnector.expColumn + " bigint NOT NULL, "
		        + DBConnector.pskColumn + " varbinary(32), "
		        + DBConnector.rpkColumn + " varbinary(255),"
		        + "PRIMARY KEY (" + DBConnector.rsIdColumn + "));";

		String createC = "CREATE TABLE IF NOT EXISTS " + DBConnector.dbName
		        + "." + DBConnector.cTable + " ("
		        + DBConnector.clientIdColumn + " varchar(255) NOT NULL, "
		        + DBConnector.defaultAud + " varchar(255), "
		        + DBConnector.defaultScope + " varchar(255), "
                + DBConnector.pskColumn + " varbinary(32), " 
                + DBConnector.rpkColumn + " varbinary(255),"
                + "PRIMARY KEY (" + DBConnector.clientIdColumn + "));";

		String createProfiles = "CREATE TABLE IF NOT EXISTS " 
		        + DBConnector.dbName + "."
		        + DBConnector.profilesTable + "(" 
		        + DBConnector.idColumn + " varchar(255) NOT NULL, " 
		        + DBConnector.profileColumn + " varchar(255) NOT NULL);";
		
		String createKeyTypes = "CREATE TABLE IF NOT EXISTS " 
		        + DBConnector.dbName + "."
		        + DBConnector.keyTypesTable + "(" 
		        + DBConnector.idColumn + " varchar(255) NOT NULL, " 
		        + DBConnector.keyTypeColumn + " enum('PSK', 'RPK', 'TST'));";

		String createScopes = "CREATE TABLE IF NOT EXISTS " 
		        + DBConnector.dbName + "."
		        + DBConnector.scopesTable + "(" 
		        + DBConnector.rsIdColumn + " varchar(255) NOT NULL, " 
		        + DBConnector.scopeColumn + " varchar(255) NOT NULL);";
	      
		String createTokenTypes = "CREATE TABLE IF NOT EXISTS " 
		        + DBConnector.dbName + "."
		        + DBConnector.tokenTypesTable + "(" 
		        + DBConnector.rsIdColumn + " varchar(255) NOT NULL, " 
		        + DBConnector.tokenTypeColumn + " enum('CWT', 'REF', 'TST'));";

		String createAudiences = "CREATE TABLE IF NOT EXISTS " 
		        + DBConnector.dbName + "."
		        + DBConnector.audiencesTable + "(" 
		        + DBConnector.rsIdColumn + " varchar(255) NOT NULL, "
		        + DBConnector.audColumn + " varchar(255) NOT NULL);";

		String createCose =  "CREATE TABLE IF NOT EXISTS " 
		        + DBConnector.dbName + "."
                + DBConnector.coseTable + "(" 
                + DBConnector.rsIdColumn + " varchar(255) NOT NULL, "
                + DBConnector.coseColumn + " varchar(255) NOT NULL);";
				
		String createClaims = "CREATE TABLE IF NOT EXISTS " 
		        + DBConnector.dbName + "."
		        + DBConnector.claimsTable + "(" 
		        + DBConnector.cidColumn + " varchar(255) NOT NULL, " 
		        + DBConnector.claimNameColumn + " varchar(8) NOT NULL," 
		        + DBConnector.claimValueColumn + " varbinary(255));";
		
		String createCtiCtr = "CREATE TABLE IF NOT EXISTS " 
                + DBConnector.dbName + "."
                + DBConnector.ctiCounterTable + "(" 
                + DBConnector.ctiCounterColumn + " int unsigned);";
		
		String initCtiCtr = "INSERT INTO "
                + DBConnector.dbName + "." + DBConnector.ctiCounterTable
                + " VALUES (0);";
		
		Properties connectionProps = new Properties();
		connectionProps.put("user", "root");
		connectionProps.put("password", rootPwd);
		try (Connection rootConn = DriverManager.getConnection(
		        this.defaultDbUrl, connectionProps);
		        Statement stmt = rootConn.createStatement();) {
		    stmt.execute(createDB);
		    stmt.execute(createRs);
		    stmt.execute(createC);
		    stmt.execute(createProfiles);
            stmt.execute(createKeyTypes);
            stmt.execute(createScopes);
            stmt.execute(createTokenTypes);
            stmt.execute(createAudiences);
            stmt.execute(createCose);
            stmt.execute(createClaims);
            stmt.execute(createCtiCtr);
            stmt.execute(initCtiCtr);
            rootConn.close();
            stmt.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
	}
	
	/**
	 * Close the connections. After this any other method calls to this
	 * object will lead to an exception.
	 * 
	 * @throws AceException
	 */
	@Override
	public synchronized void close() throws AceException {
		try {
            this.conn.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
	}
	
	/**
	 * Returns a common value that the client supports (first param )
	 * and that every RS supports (every set in the map)
	 * 
	 * @param client  the set of values the client supports
	 * @param rss  the map of sets of values the rs support
	 * 
	 * @return  the common value or null if there isn't any
	 */
	private static String getCommonValue(Set<String> client, 
	        Map<String,Set<String>> rss) {
	    for (String clientVal : client) {
            boolean isSupported = true;
            for (String rs : rss.keySet()) {
                if (!rss.get(rs).contains(clientVal)) {
                    isSupported = false;
                }
            }
            if (isSupported) {
                return clientVal;
            }
        }
        return null;
	}
	
    @Override
    public synchronized String getSupportedProfile(
            String clientId, String audience) throws AceException {
        Map<String, Set<String>> rsProfiles = new HashMap<>();
        Set<String> clientProfiles = new HashSet<>();
        try {
            this.selectProfiles.setString(1, audience);
            this.selectProfiles.setString(2, clientId);
            ResultSet result = this.selectProfiles.executeQuery();
            this.selectProfiles.clearParameters();

            while(result.next()) {
                String id = result.getString(DBConnector.idColumn);
                String profile = result.getString(DBConnector.profileColumn);
                if (id.equals(clientId)) {
                    clientProfiles.add(profile);
                } else if (rsProfiles.containsKey(id)) {
                    Set<String> foo = rsProfiles.get(id);
                    foo.add(profile);
                    rsProfiles.put(id, foo);
                } else {
                    Set<String> bar = new HashSet<>();
                    bar.add(profile);
                    rsProfiles.put(id, bar);
                }
            }
        result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
        return getCommonValue(clientProfiles, rsProfiles);
      
    }

    @Override
    public synchronized String getSupportedPopKeyType(
            String clientId, String aud) throws AceException {
        Map<String, Set<String>> rsKeyTypes = new HashMap<>();
        Set<String> clientKeyTypes = new HashSet<>();
        try {
            this.selectKeyTypes.setString(1, aud);
            this.selectKeyTypes.setString(2, clientId);
            ResultSet result = this.selectKeyTypes.executeQuery();
            this.selectKeyTypes.clearParameters();
            while(result.next()) {
                String id = result.getString(DBConnector.idColumn);
                String keyType = result.getString(DBConnector.keyTypeColumn);
                if (id.equals(clientId)) {
                    clientKeyTypes.add(keyType);
                } else if (rsKeyTypes.containsKey(id)) {
                    Set<String> foo = rsKeyTypes.get(id);
                    foo.add(keyType);
                    rsKeyTypes.put(id, foo);
                } else {
                    Set<String> bar = new HashSet<>();
                    bar.add(keyType);
                    rsKeyTypes.put(id, bar);
                }
            }
            result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
        return getCommonValue(clientKeyTypes, rsKeyTypes);
        
    }
    
    @Override
    public  synchronized Integer getSupportedTokenType(String aud) 
            throws AceException {
        //Note: We store the token types as Strings in the DB
        Map<String, Set<String>> tokenTypes = new HashMap<>();
        try {
            this.selectTokenTypes.setString(1, aud);
            ResultSet result = this.selectTokenTypes.executeQuery();
            this.selectTokenTypes.clearParameters();
            while(result.next()) {
                String id = result.getString(DBConnector.rsIdColumn);
                String tokenType = result.getString(
                        DBConnector.tokenTypeColumn);
               if (tokenTypes.containsKey(id)) {
                    Set<String> foo = tokenTypes.get(id);
                    foo.add(tokenType);
                    tokenTypes.put(id, foo);
                } else {
                    Set<String> bar = new HashSet<>();
                    bar.add(tokenType);
                    tokenTypes.put(id, bar);
                } 
            }
            result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
        
        Set<String> refSet = null;
        for (Entry<String, Set<String>> rs : tokenTypes.entrySet()) {
            if (refSet == null) {
                refSet = new HashSet<>();
                refSet.addAll(rs.getValue());
            } else {
                Set<String> iterSet = new HashSet<>(refSet);
                for (String tokenType : iterSet) {
                    if (!rs.getValue().contains(tokenType)) {
                        refSet.remove(tokenType);
                    }
                }
                if (refSet.isEmpty()) {
                    return null;
                }
            }
        }
        //Get the first remaining value
        if (refSet != null && !refSet.isEmpty()) {
            String tokenType = refSet.iterator().next();
            for (int i=0; i<AccessTokenFactory.ABBREV.length; i++) {
                if (tokenType.equals(AccessTokenFactory.ABBREV[i])) {
                    return i;
                }
            }
        } 
        //The audience was empty or didn't support any token types
        throw new AceException("No token types found for audience: " + aud);        
    }
    
    @Override
    public synchronized COSEparams getSupportedCoseParams(String aud) 
            throws AceException, CoseException {
        Map<String, Set<String>> cose = new HashMap<>();
        try {
            this.selectCOSE.setString(1, aud);
            ResultSet result = this.selectCOSE.executeQuery();
            this.selectCOSE.clearParameters();
            while(result.next()) {
                String id = result.getString(DBConnector.rsIdColumn);
                String coseParam = result.getString(
                        DBConnector.coseColumn);
               if (cose.containsKey(id)) {
                    Set<String> foo = cose.get(id);
                    foo.add(coseParam);
                    cose.put(id, foo);
                } else {
                    Set<String> bar = new HashSet<>();
                    bar.add(coseParam);
                    cose.put(id, bar);
                } 
            }
            result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
        
        Set<String> refSet = null;
        for (Entry<String, Set<String>> rs : cose.entrySet()) {
            if (refSet == null) {
                refSet = new HashSet<>();
                refSet.addAll(rs.getValue());
            } else {
                for (String tokenType : refSet) {
                    if (!rs.getValue().contains(tokenType)) {
                        refSet.remove(tokenType);
                    }
                }
                if (refSet.isEmpty()) {
                    return null;
                }
            }
        }
        
        //Get the first remaining value
        if (refSet != null && !refSet.isEmpty()) {
            String result = refSet.iterator().next();
            return COSEparams.parse(result);
        }
        
        //The audience was empty or didn't support any token types
        throw new AceException("No cose parameters found for audience: " + aud);                         
    }
    
    @Override
    public synchronized boolean isScopeSupported(String aud, String scope)
            throws AceException {
        Set<String> allRS = getRSS(aud);
        Set<String> supportingSope = new HashSet<>();
        try {
            this.selectScopes.setString(1, aud);
            ResultSet result = this.selectScopes.executeQuery();
            this.selectScopes.clearParameters();
            while (result.next()) {
                String scp = result.getString(DBConnector.scopeColumn);
                if (scp.equals(scope)) {
                    supportingSope.add(result.getString(DBConnector.rsIdColumn));
                }
            }
            result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
        if (supportingSope.containsAll(allRS)) {
            return true;
        }
        return false;
    }
 
    @Override
    public synchronized String getDefaultScope(String client) 
            throws AceException {
        try {
            this.selectDefaultScope.setString(1, client);
            ResultSet result = this.selectDefaultScope.executeQuery();
            this.selectDefaultScope.clearParameters();
            if (result.next()) {
                String scope = result.getString(DBConnector.defaultScope);
                result.close();
                return scope;
            }
            result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
        return null;
    }

    @Override
    public synchronized String getDefaultAudience(String client) throws 
            AceException {
        try {
            this.selectDefaultAudience.setString(1, client);
            ResultSet result = this.selectDefaultAudience.executeQuery();
            this.selectDefaultAudience.clearParameters();
            if (result.next()) {
                String aud = result.getString(DBConnector.defaultAud);
                result.close();
                return aud;
            }
            result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
        return null;
    }
    
    @Override
    public synchronized Set<String> getRSS(String aud) throws AceException {
       Set<String> rss = new HashSet<>();
        try {
            this.selectRS.setString(1, aud);
            ResultSet result = this.selectRS.executeQuery();
            this.selectRS.clearParameters();
            while (result.next()) {
                rss.add(result.getString(DBConnector.rsIdColumn));
            }
            result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
        if (rss.isEmpty()) {
            return null;
        }
        return rss;
    }
    
    @Override
    public synchronized long getExpTime(String rs) throws AceException {
        long smallest = Long.MAX_VALUE;
        try {
            this.selectExpiration.setString(1, rs);
            ResultSet result = this.selectExpiration.executeQuery();
            this.selectExpiration.clearParameters();
            while (result.next()) {
                long val = result.getLong(DBConnector.expColumn);
                if (val < smallest) {
                    smallest = val;
                }
            }
            result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
        return smallest;
    }
    

    @Override
    public synchronized Set<String> getAudiences(String rs) 
            throws AceException {
        Set<String> auds = new HashSet<>();
        try {
            this.selectAudiences.setString(1, rs);
            ResultSet result = this.selectAudiences.executeQuery();
            this.selectAudiences.clearParameters();
            while (result.next()) {
                auds.add(result.getString(DBConnector.audColumn));      
            }
            result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
        return auds;
    }

    @Override
    public synchronized byte[] getRsPSK(String rs) throws AceException {
        try {
            this.selectRsPSK.setString(1, rs);
            ResultSet result = this.selectRsPSK.executeQuery();
            this.selectRsPSK.clearParameters();
            byte[] key = null;
            if (result.next()) {
                key = result.getBytes(DBConnector.pskColumn);
            }
            result.close();
            return key;
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
    }

    @Override
    public synchronized OneKey getRsRPK(String rs) throws AceException {
        try {
            this.selectRsRPK.setString(1, rs);
            ResultSet result = this.selectRsRPK.executeQuery();
            this.selectRsRPK.clearParameters();
            byte[] key = null;
            if (result.next()) {
                key = result.getBytes(DBConnector.rpkColumn);
            }
            result.close();
            if (key != null) {
                CBORObject cKey = CBORObject.DecodeFromBytes(key);
                return new OneKey(cKey);
            }
            return null;
        } catch (SQLException | CoseException e) {
            throw new AceException(e.getMessage());
        }
    }
    
    @Override
    public synchronized byte[] getCPSK(String client) throws AceException {
        try {
            this.selectCPSK.setString(1, client);
            ResultSet result = this.selectCPSK.executeQuery();
            this.selectCPSK.clearParameters();
            byte[] key = null;
            if (result.next()) {
                key = result.getBytes(DBConnector.pskColumn);
            }
            result.close();
            return key;
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
    }

    @Override
    public synchronized OneKey getCRPK(String client) throws AceException {
        try {
            this.selectCRPK.setString(1, client);
            ResultSet result = this.selectCRPK.executeQuery();
            this.selectCRPK.clearParameters();
            byte[] key = null;
            if (result.next()) {
                key = result.getBytes(DBConnector.rpkColumn);
            }
            result.close();
            if (key != null) {
                CBORObject cKey = CBORObject.DecodeFromBytes(key);
                return new OneKey(cKey);
            }
            return null;
        } catch (SQLException | CoseException e) {
            throw new AceException(e.getMessage());
        }
    }

    @Override
    public synchronized void addRS(String rs, Set<String> profiles, 
            Set<String> scopes, Set<String> auds, Set<String> keyTypes, 
            Set<Integer> tokenTypes, Set<COSEparams> cose, long expiration, 
            byte[] sharedKey, OneKey publicKey) throws AceException {

        if (rs == null || rs.isEmpty()) {
            throw new AceException(
                    "RS must have non-null, non-empty identifier");
        }
        
        if (sharedKey == null && publicKey == null) {
            throw new AceException("Cannot register a RS without a key");
        }
        
        if (profiles.isEmpty()) {
            throw new AceException("RS must support at least one profile");
        }
        
        if (tokenTypes.isEmpty()) {
            throw new AceException("RS must support at least one token type");
        }
        
        if (keyTypes.isEmpty()) {
            throw new AceException("RS must support at least one PoP key type");
        }
        
        if (expiration <= 0L) {
            throw new AceException("RS must have default expiration time > 0");
        }
        
        
        
        // Prevent adding an rs that has an identifier that is equal to an 
        // existing audience
        try {
            this.selectRS.setString(1, rs);
            ResultSet result = this.selectRS.executeQuery();
            this.selectRS.clearParameters();
            if (result.next()) {
                result.close();
                throw new AceException("RS id not allowed: " + rs);
            }
            result.close();
                
            this.insertRS.setString(1, rs);
            this.insertRS.setLong(2, expiration);
            this.insertRS.setBytes(3, sharedKey);
            if (publicKey != null) {
                this.insertRS.setBytes(4, publicKey.EncodeToBytes());
            } else {
                this.insertRS.setBytes(4, null);
            }
            this.insertRS.execute();
            this.insertRS.clearParameters();
            
            for (String profile : profiles) {
                this.insertProfile.setString(1, rs);
                this.insertProfile.setString(2, profile);
                this.insertProfile.execute();
            }
            this.insertProfile.clearParameters();
            
            for (String scope : scopes) {
                this.insertScope.setString(1, rs);
                this.insertScope.setString(2, scope);
                this.insertScope.execute();
            }
            this.insertScope.clearParameters();
            
            for (String aud : auds) {
                this.insertAudience.setString(1, rs);
                this.insertAudience.setString(2, aud);
                this.insertAudience.execute();
            }
            this.insertAudience.clearParameters();
            
            //The RS always recognizes itself as a singleton audience
            this.insertAudience.setString(1, rs);
            this.insertAudience.setString(2, rs);
            this.insertAudience.execute();
            this.insertAudience.clearParameters();
            
            for (String keyType : keyTypes) {
                this.insertKeyType.setString(1, rs);
                this.insertKeyType.setString(2, keyType);
                this.insertKeyType.execute();
            }
            this.insertKeyType.clearParameters();
            
            for (int tokenType : tokenTypes) {
                this.insertTokenType.setString(1, rs);
                this.insertTokenType.setString(2, 
                        AccessTokenFactory.ABBREV[tokenType]);
                this.insertTokenType.execute();
            }
            this.insertTokenType.clearParameters();
            
            for (COSEparams coseP : cose) {
                this.insertCose.setString(1, rs);
                this.insertCose.setString(2, coseP.toString());
                this.insertCose.execute();
            }
            this.insertCose.clearParameters();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
    }

    @Override
    public synchronized void deleteRS(String rs) throws AceException {
        try {
            this.deleteRS.setString(1, rs);
            this.deleteRS.execute();
            this.deleteRS.clearParameters();

            this.deleteProfiles.setString(1, rs);
            this.deleteProfiles.execute();
            this.deleteProfiles.clearParameters();

            this.deleteScopes.setString(1, rs);
            this.deleteScopes.execute();
            this.deleteScopes.clearParameters();

            this.deleteAudiences.setString(1, rs);
            this.deleteAudiences.execute();
            this.deleteAudiences.clearParameters();

            this.deleteKeyTypes.setString(1, rs);
            this.deleteKeyTypes.execute();
            this.deleteKeyTypes.clearParameters();

            this.deleteTokenTypes.setString(1, rs);
            this.deleteTokenTypes.execute();
            this.deleteTokenTypes.clearParameters();    

            this.deleteCose.setString(1, rs);
            this.deleteCose.execute();
            this.deleteCose.clearParameters();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
    }

    @Override
    public synchronized void addClient(String client, Set<String> profiles,
            String defaultScope, String defaultAud, Set<String> keyTypes,
            byte[] sharedKey, OneKey publicKey) 
                    throws AceException {
        try {
            if (sharedKey == null && publicKey == null) {
                throw new AceException(
                        "Cannot register a client without a key");
            }
            this.insertClient.setString(1, client);
            this.insertClient.setString(2, defaultAud);
            this.insertClient.setString(3, defaultScope);
            this.insertClient.setBytes(4, sharedKey);
            if (publicKey != null) {
                this.insertClient.setBytes(5, publicKey.EncodeToBytes());
            } else {
                this.insertClient.setBytes(5, null);
            }
            this.insertClient.execute();
            this.insertClient.clearParameters();

            for (String profile : profiles) {
                this.insertProfile.setString(1, client);
                this.insertProfile.setString(2, profile);
                this.insertProfile.execute();
            }
            this.insertProfile.clearParameters();

            for (String keyType : keyTypes) {
                this.insertKeyType.setString(1, client);
                this.insertKeyType.setString(2, keyType);
                this.insertKeyType.execute();
            }
            this.insertKeyType.clearParameters();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
    }

    @Override
    public synchronized void deleteClient(String client) throws AceException {
        try {
            this.deleteClient.setString(1, client);
            this.deleteClient.execute();
            this.deleteClient.clearParameters();

            this.deleteProfiles.setString(1, client);
            this.deleteProfiles.execute();
            this.deleteProfiles.clearParameters();

            this.deleteKeyTypes.setString(1, client);
            this.deleteKeyTypes.execute();
            this.deleteKeyTypes.clearParameters(); 
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }   
    }
    
    @Override
    public synchronized void addToken(String cti, 
            Map<String, CBORObject> claims) throws AceException {
        try {
            for (Entry<String, CBORObject> claim : claims.entrySet()) {
                this.insertClaim.setString(1, cti);
                this.insertClaim.setString(2, claim.getKey());
                this.insertClaim.setBytes(3, claim.getValue().EncodeToBytes());
                this.insertClaim.execute();
            }
            this.insertClaim.clearParameters();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }       
    }

    @Override
    public synchronized void deleteToken(String cti) throws AceException {
        try {
            this.deleteClaims.setString(1, cti);
            this.deleteClaims.execute();
            this.deleteClaims.clearParameters();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }   
    }

    @Override
    public synchronized void purgeExpiredTokens(long now) throws AceException {
        try {
            ResultSet result = this.selectExpirationTime.executeQuery();
            while (result.next()) {
                byte[] rawTime = result.getBytes(DBConnector.claimValueColumn);
                CBORObject cborTime = CBORObject.DecodeFromBytes(rawTime);
                long time = cborTime.AsInt64();
                if (now > time) {
                    deleteToken(result.getString(DBConnector.cidColumn));
                }
            }
            result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        } 
    }

    @Override
    public synchronized Map<String, CBORObject> getClaims(String cti) 
            throws AceException {
        Map<String, CBORObject> claims = new HashMap<>();
        try {
            this.selectClaims.setString(1, cti);
            ResultSet result = this.selectClaims.executeQuery();
            this.selectClaims.clearParameters();
            while (result.next()) {
                String claimName 
                    = result.getString(DBConnector.claimNameColumn);
                CBORObject cbor = CBORObject.DecodeFromBytes(
                        result.getBytes(DBConnector.claimValueColumn));
                claims.put(claimName, cbor);
            }
            result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        } 
        return claims;
    }

    @Override
    public synchronized Long getCtiCounter() throws AceException {
        Long l = -1L;
        try {
            ResultSet result = this.selectCtiCtr.executeQuery();
            if (result.next()) {
                l = result.getLong(DBConnector.ctiCounterColumn);
            }
            result.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
        return l;
    }

    @Override
    public synchronized void saveCtiCounter(Long cti) throws AceException {
        try {
            this.updateCtiCtr.setLong(1, cti);
            this.updateCtiCtr.execute();
            this.updateCtiCtr.clearParameters();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }    
    }
    
    /**
     * Creates the user that manages this database.
     * 
     * @param rootPwd  the database root password
     * @param username  the name of the user
     * @param userPwd   the password for the user
     * @param dbUrl  the URL of the database
     * 
     * @throws AceException 
     */
    public synchronized static void createUser(String rootPwd, String username, 
            String userPwd, String dbUrl) throws AceException {
        Properties connectionProps = new Properties();
        connectionProps.put("user", "root");
        connectionProps.put("password", rootPwd);
        String cUser = "CREATE USER '" + username 
                + "'@'localhost' IDENTIFIED BY '" + userPwd 
                + "';";
        String authzUser = "GRANT DELETE, INSERT, SELECT, UPDATE ON "
               + DBConnector.dbName + ".* TO '" + username + "'@'localhost';";
        try (Connection rootConn = DriverManager.getConnection(
                dbUrl, connectionProps);
                Statement stmt = rootConn.createStatement();) {
            stmt.execute(cUser);
            stmt.execute(authzUser);
            stmt.close();
        } catch (SQLException e) {
            throw new AceException(e.getMessage());
        }
    }
    
}