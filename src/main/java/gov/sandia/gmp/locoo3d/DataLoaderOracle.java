/**
 * Copyright 2009 Sandia Corporation. Under the terms of Contract
 * DE-AC04-94AL85000 with Sandia Corporation, the U.S. Government
 * retains certain rights in this software.
 * 
 * BSD Open Source License.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *    * Redistributions of source code must retain the above copyright notice,
 *      this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of Sandia National Laboratories nor the names of its
 *      contributors may be used to endorse or promote products derived from
 *      this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package gov.sandia.gmp.locoo3d;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;

import gov.sandia.gmp.baseobjects.PropertiesPlusGMP;
import gov.sandia.gmp.baseobjects.Receiver;
import gov.sandia.gmp.baseobjects.Source;
import gov.sandia.gmp.baseobjects.geovector.GeoVector;
import gov.sandia.gmp.baseobjects.globals.DBTableTypes;
import gov.sandia.gmp.baseobjects.globals.GMPGlobals;
import gov.sandia.gmp.predictorfactory.PredictorFactory;
import gov.sandia.gmp.util.containers.arraylist.ArrayListLong;
import gov.sandia.gmp.util.exceptions.GMPException;
import gov.sandia.gmp.util.globals.GMTFormat;
import gov.sandia.gmp.util.globals.Globals;
import gov.sandia.gmp.util.logmanager.ScreenWriterOutput;
import gov.sandia.gmp.util.propertiesplus.PropertiesPlusException;
import gov.sandia.gnem.dbtabledefs.nnsa_kb_core_extended.ArrivalExtended;
import gov.sandia.gnem.dbtabledefs.nnsa_kb_core_extended.NetworkExtended;
import gov.sandia.gnem.dbtabledefs.nnsa_kb_core_extended.OriginExtended;
import gov.sandia.gnem.dbtabledefs.nnsa_kb_core_extended.Schema;

/**
 * <p>Title: LocOO</p>
 *
 * <p>Description: Seismic Event Locator</p>
 *
 * <p>Copyright: Copyright (c) 2008</p>
 *
 * <p>Company: Sandia National Laboratories</p>
 *
 * @author Sandy Ballard
 * @version 1.0
 */
/***
 * <p>
 * Title:
 * </p>
 * 
 * <p>
 * Description:
 * </p>
 * 
 * <p>
 * Copyright: Copyright (c) 2008
 * </p>
 * 
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */
public class DataLoaderOracle implements DataLoader
{

	private PropertiesPlusGMP properties;
	private ScreenWriterOutput logger;
	private ScreenWriterOutput errorlog;

	protected Schema inputSchema;

	protected Schema outputSchema;

	protected NetworkExtended network;
	
	/**
	 * Map from sta/phase -> tt,az,sh corrections for master event relocation.
	 * Units are tt (sec), az (radians), sh (sec/radian).
	 * Default values are Assoc.TIMERES_NA, Assoc.AZRES_NA, Assoc.SLORES_NA.
	 */
	private HashMap<String, double[]> masterEventCorrections;

	private AtomicLong nextSourceId = null;
	
	public DataLoaderOracle()
	{
		
	}

	/**
	 * Initializer
	 * 
	 * @param properties
	 *            Properties
	 * @throws Exception 
	 */
	@Override
	public void initialize(PropertiesPlusGMP properties, 
			ScreenWriterOutput logger, ScreenWriterOutput errorlog) throws Exception
	{
		this.properties = properties;
		this.logger = logger;
		this.errorlog = errorlog;

		this.inputSchema = new Schema("dbInput", properties, false);

		if (logger.getVerbosity() > 0) logger.writeln("\n"+inputSchema.toString());

		if (properties.containsKey("dbOutputUserName"))
		{
			this.outputSchema = new Schema("dbOutput", properties, true);

			if (logger.getVerbosity() > 0) logger.writeln(outputSchema.toString());
		}
		else 
			this.outputSchema = null;

		String masterEventWhereClause = properties.getProperty("masterEventWhereClause");
		if (masterEventWhereClause != null)
		{
			String maserSchemaPrefix = properties.getProperty("masterEventSchema", "dbInput");
			
			Schema masterSchema = new Schema(maserSchemaPrefix, properties, false);
			ArrayList<String> executedSql = new ArrayList<String>();
			
			HashSet<OriginExtended> masterEvents = OriginExtended.readOriginExtended(masterSchema, null, 
					masterEventWhereClause, properties.getProperty("masterAssocWhereClause", ""), executedSql);
			
			if (masterEvents.size() == 1)
			{
				OriginExtended masterEvent = masterEvents.iterator().next();

				// Create the predictors, using the PredictorFactory
				PredictorFactory predictors = new PredictorFactory(properties,"loc_predictor_type");

				masterEventCorrections = DataLoader.getMasterEventCorrections(masterEvent,
						predictors, logger, "masterEventSchema:\n"+masterSchema.toString());
				
//				masterEventCorrections = new HashMap<String, double[]>(masterEvent.getAssocs().size());
//
//				PredictionRequest request = new PredictionRequest();
//				request.setDefining(true);
//				request.setSource(new Source(masterEvent));
//				request.setRequestedAttributes(
//						EnumSet.of(GeoAttributes.TRAVEL_TIME, GeoAttributes.AZIMUTH_DEGREES, GeoAttributes.SLOWNESS_DEGREES));
//				
//				double TIME_NA = gov.sandia.gnem.dbtabledefs.nnsa_kb_core.Arrival.TIME_NA;
//				double AZIMUTH_NA = gov.sandia.gnem.dbtabledefs.nnsa_kb_core.Arrival.AZIMUTH_NA;
//				double SLOW_NA = gov.sandia.gnem.dbtabledefs.nnsa_kb_core.Arrival.SLOW_NA;
//				String format = "  %-6s %-6s %2s %8.3f%n";
//
//				for (AssocExtended assoc : masterEvent.getAssocs().values())
//				{
//					if (assoc.isTimedef() || assoc.isAzdef() || assoc.isSlodef())
//					{
//						SeismicPhase phase = SeismicPhase.valueOf(assoc.getPhase());
//						PredictorInterface predictor = predictors.getPredictor(phase);
//						if (predictor != null)
//						{
//							request.setReceiver(new Receiver(assoc.getSite()));
//							request.setPhase(phase);
//
//							PredictionInterface prediction = predictor.getPrediction(request);
//							if (prediction.isValid())
//							{
//								double[] corr = new double[] {Assoc.TIMERES_NA, Assoc.AZRES_NA, Assoc.SLORES_NA};
//								
//								if (assoc.isTimedef() && assoc.getArrival().getTime() != TIME_NA
//										&& prediction.getAttribute(GeoAttributes.TRAVEL_TIME) != Globals.NA_VALUE)
//									corr[0] = assoc.getArrival().getTime()-masterEvent.getTime()-
//										prediction.getAttribute(GeoAttributes.TRAVEL_TIME);
//								
//								if (assoc.isAzdef() && assoc.getArrival().getAzimuth() != AZIMUTH_NA
//										&& prediction.getAttribute(GeoAttributes.AZIMUTH_DEGREES) != AZIMUTH_NA)
//								{
//									corr[1] = Math.toRadians(assoc.getArrival().getAzimuth()
//											-prediction.getAttribute(GeoAttributes.AZIMUTH_DEGREES));
//									if (corr[1] < -Math.PI) 
//										corr[1] += 2*Math.PI;
//									else if (corr[1] > Math.PI) 
//										corr[1] -= 2*Math.PI;
//								}
//
//								if (assoc.isSlodef() && assoc.getArrival().getSlow() != SLOW_NA
//										&& prediction.getAttribute(GeoAttributes.SLOWNESS_DEGREES) != SLOW_NA)
//									corr[2] = Math.toDegrees(assoc.getArrival().getSlow()
//											-prediction.getAttribute(GeoAttributes.SLOWNESS_DEGREES));
//								
//								masterEventCorrections.put(String.format("%s/%s", assoc.getSta(), assoc.getPhase()), corr);
//							}
//						}
//					}
//				}
//				
//				String format = "  %-6s %-6s %2s %8.3f%n";
//				if (logger.getVerbosity() > 0)
//				{
//					logger.writeln("masterEventSchema:\n"+masterSchema.toString());
//					logger.write(String.format("masterEvent loaded:%n"
//							+ "  Evid    = %d%n"
//							+ "  Orid    = %d%n"
//							+ "  Lat     = %11.5f%n"
//							+ "  Lon     = %11.5f%n"
//							+ "  Depth   = %8.3f%n"
//							+ "  Time    = %15.3f%n"
//							+ "  Jdate   = %d%n"
//							+ "  NAssocs = %d%n%n",
//							masterEvent.getEvid(),
//							masterEvent.getOrid(),
//							masterEvent.getLat(),
//							masterEvent.getLon(),
//							masterEvent.getDepth(),
//							masterEvent.getTime(),
//							masterEvent.getJdate(),
//							masterEvent.getAssocs().size()
//							));
//					for (String mec : new TreeSet<String>(masterEventCorrections.keySet()))
//					{
//						double[] corr = masterEventCorrections.get(mec);
//						String[] staPhase = mec.split("/");
//						if (corr[0] != Assoc.TIMERES_NA)
//							logger.write(String.format(format, staPhase[0],staPhase[1], "tt", corr[0]));
//						if (corr[1] != Assoc.AZRES_NA)
//							logger.write(String.format(format, staPhase[0], staPhase[1], "az", corr[1]));
//						if (corr[2] != Assoc.SLORES_NA)
//							logger.write(String.format(format, staPhase[0], staPhase[1], "sh", corr[2]));
//					}
//					logger.writeln();
//				}

			}
			else
			{
				StringBuffer error = new StringBuffer();
				error.append(String.format("The following sql failed to properly load the master origin "
						+ "(%d origins loaded but 1 is allowed):\n", masterEvents.size()));
				for (String s : executedSql)
					error.append(s+"\n");
				throw new Exception(error.toString());
			}			
			masterSchema.close();
		}
		else
			masterEventCorrections = new HashMap<String, double[]>();

		try
		{
			String originWhereClause = properties.getProperty("dbInputWhereClause");
			String originSql = originWhereClause.startsWith("where ") 
					? String.format("select orid from %s %s", inputSchema.getTableName("origin"), originWhereClause)
							: String.format("select orid from %s where %s", inputSchema.getTableName("origin"), originWhereClause);

					String assocWhereClause = properties.getProperty("dbInputAssocClause", "");
					if (assocWhereClause.length() > 0
							&& !assocWhereClause.toLowerCase().startsWith("and "))
						assocWhereClause = "and " + assocWhereClause;

					String assocSql = String.format("select sta from %s where orid in (%s) %s",
							inputSchema.getTableName("assoc"), originSql, assocWhereClause);

					String siteSql = String.format("select * from %s where sta in (%s)", inputSchema.getTableName("site"), assocSql);

					if (logger.getVerbosity() > 0)
						logger.writeln("Loading site information using "+siteSql);

					long timer = System.currentTimeMillis();

					network = new NetworkExtended(inputSchema.getConnection(), siteSql);

					if (properties.containsKey("masterEventWhereClause"))
					{
						OriginExtended.readOriginExtended(inputSchema, network, properties.getProperty("masterEventWhereClause"), 
								properties.getProperty("dbInputAssocClause", ""), null);
					}

					timer = System.currentTimeMillis()-timer;

					if (logger.getVerbosity() > 0)
						logger.writeln(String.format("Loaded %d sites in %d msec", network.getSites().size(), timer));
		}
		catch(Exception ex)
		{
			System.err.println("Exception thrown while trying to load site information from the inputSchema.");
			ex.printStackTrace();
			network = null;
		}
		
		if (outputSchema != null)
		{
			nextSourceId = getNextId(DBTableTypes.SOURCE);
			if (nextSourceId == null)
				nextSourceId = getNextId(DBTableTypes.ORIGIN);
			//nextPredictionId = iodb.getNextId(DBTableTypes.PREDICTION);
		}
	}

	/**
	 * Query the database to retrieve batches of orid|sourceid such that each batch has
	 * less than some number of time defining phases.  Returns a 2D ragged array of longs.
	 * The first index spans the batches and the second spans the orids|sourceids in each
	 * batch.
	 * <p>The maximum number of time defining phases in a batch is retrieve from the 
	 * input property file with property batchSizeNdef, which defaults to 100.  Then
	 * the following sql statement is executed 
	 * <p>select orid, ndef from origin_table where <whereClause> order by ndef descending
	 * <p>The returned information is used to split the orids up into batches, as described above.
	 * @param schema
	 * @param ndefMax desired maximum number of defining phases per batch of orids.
	 * @param whereClause
	 * @return 2D ragged array of orids
	 * @throws GMPException
	 * @throws IOException
	 * @throws SQLException 
	 */
	@Override
	public ArrayList<ArrayListLong> readTaskSourceIds() throws SQLException, GMPException

	{
		int ndefMax = Math.min(1000, properties.getInt("batchSizeNdef", 1000));

		String whereClause = properties.getProperty("dbInputWhereClause");

		if (whereClause == null)
			throw new PropertiesPlusException("Property dbInputWhereClause is not specified.");

		whereClause = whereClause.toLowerCase().startsWith("where ") ? whereClause : "where "+whereClause;

		// query will be something like: 
		// select orid, ndef from origin_table where <whereClause> order by ndef descending

		String sql;

		if (inputSchema.getTableName("source") != null) 
			sql = String.format("select source, numassoc from %s %s order by numassoc desc",
					inputSchema.getTableName("source"), whereClause);

		else sql = String.format("select orid, ndef from %s %s order by ndef desc",
				inputSchema.getTableName("origin"), whereClause);

		if (logger.isOutputOn() && logger.getVerbosity() > 0)
			logger.write(String.format("Executing sql:%n%s%n",sql.toString()));

		long timer = System.nanoTime();

		Statement statement = inputSchema.getConnection().createStatement();
		ResultSet resultSet = statement.executeQuery(sql);

		timer = System.nanoTime()-timer;

		int count=0;
		ArrayList<ArrayListLong> batches = new ArrayList<ArrayListLong>();
		{
			ArrayListLong batch = new ArrayListLong();
			batches.add(batch);
			long n=0, orid, ndef;
			while (resultSet.next())
			{
				++count;
				orid = resultSet.getLong(1);
				ndef = resultSet.getLong(2);
				if (ndef <= 0)
					ndef = 10;
				if (batch.size() > 0 && n+ndef > ndefMax)
				{
					batch = new ArrayListLong();
					batches.add(batch);
					n = 0;
				}
				batch.add(orid);
				n += ndef;
			}
		}
		resultSet.close();
		statement.close();

		if (logger.isOutputOn() && logger.getVerbosity() > 0)
			logger.write(String.format("Query returned %d records in %s%n", 
					count, GMPGlobals.ellapsedTime(timer*1e-9)));


		if (logger.isOutputOn() && logger.getVerbosity() > 0)
			logger.write(String.format("%d Sources divided among %d batches with number of time defining phases < %d in each batch%n",
					count, batches.size(), ndefMax));

		long check = 0;
		for (ArrayListLong batch : batches)
			check += batch.size();

		if (check != count)
			throw new GMPException(String.format("Sum of batch sizes (%d) != data size (%d)",
					check, count));

		return batches;
	}

	@Override
	public LocOOTask readTaskObservations(ArrayListLong orids) 
			throws Exception 
	{
		StringBuffer oridList = new StringBuffer();
		oridList.append(Long.toString(orids.get(0)));
		for (int i=1; i<orids.size(); ++i)
			oridList.append(',').append(orids.get(i));

		// build assocWhereClause that is either empty, or looks like 'and ...'
		String assocWhereClause = properties.getProperty("dbInputAssocClause", "");
		if (assocWhereClause.length() > 0)
		{
			if (!assocWhereClause.toLowerCase().startsWith("and "))
				assocWhereClause = "and "+assocWhereClause;
			assocWhereClause = " "+assocWhereClause;
		}

		LocOOTask task;
		if (inputSchema.getTableName("source") != null)
			task = readObservationsGMP(oridList.toString(), assocWhereClause);
		else if (inputSchema.getTableName("origin") != null)
			task = readObservationsKB(oridList.toString(), assocWhereClause);
		else
			throw new GMPException("Could not determine if this is a GMP or KB schema "
					+"because it does not contain a table of type orgin or a table of type source");
		
		task.setMasterEventCorrections(masterEventCorrections);
		
		return task;
	}

	/**
	 * 
	 * @param oridList comma-delimited list of orids
	 * @param assocWhereClause either empty or looks like ' and ...'
	 * @return
	 * @throws Exception
	 */
	private LocOOTask readObservationsKB(String oridList, String assocWhereClause) throws Exception 
	{
		ArrayList<String> executedSql = new ArrayList<String>();

		HashSet<OriginExtended> origins = OriginExtended.readOriginExtended(inputSchema, network,
				String.format("orid in (%s)", oridList), assocWhereClause, executedSql);

		if (logger.isOutputOn() && logger.getVerbosity() > 0)
			for (String sql : executedSql) logger.writeln(sql);

		return new LocOOTask(properties, origins);
	}

	/**
	 * 
	 * @param oridList comma-delimited list of sourceids
	 * @param srcobsassocWhereClause either empty or looks like ' and ...'
	 * @return
	 * @throws Exception
	 */
	private LocOOTask readObservationsGMP(String sourceidList, String srcobsassocWhereClause) 
			throws SQLException, GMPException 
	{
		// TODO: Need to test this method.

		String sourceQuery = String.format("select sourceid, eventid, lat, lon, depth, origintime from %s where sourceid in (%s)",
				inputSchema.getTableName("source"), sourceidList);

		StringBuffer obsQuery = new StringBuffer("select ");
		obsQuery.append("sourceid, ");
		obsQuery.append("srcobsassoc.observationid, ");		
		obsQuery.append("observation.receiverid, "); // receiverId
		obsQuery.append("receiver.sta, ");
		obsQuery.append("receiver.lat, ");
		obsQuery.append("receiver.lon, ");
		obsQuery.append("receiver.elev, ");
		obsQuery.append("receiver.ondate, ");
		obsQuery.append("receiver.offdate, ");
		obsQuery.append("'-', ");

		obsQuery.append("srcobsassoc.phase, ");
		obsQuery.append("observation.time, ");
		obsQuery.append("observation.deltim, ");
		obsQuery.append("srcobsassoc.timedef, ");
		obsQuery.append("observation.azimuth, ");
		obsQuery.append("observation.delaz, ");
		obsQuery.append("srcobsassoc.azdef, ");
		obsQuery.append("observation.slow, ");
		obsQuery.append("observation.delslo, ");
		obsQuery.append("srcobsassoc.slodef ");

		obsQuery.append("from ");
		obsQuery.append(inputSchema.getTableName("srcobsassoc")).append(" srcobsassoc, ");
		obsQuery.append(inputSchema.getTableName("observation")).append(" observation, ");
		obsQuery.append(inputSchema.getTableName("receiver")).append(" receiver ");
		obsQuery.append(String.format("where sourceid in (%s) ", sourceidList));
		obsQuery.append("and srcobsassoc.observationid=observation.observationid ");
		obsQuery.append("and arrival.receiverid=receiver.receiverid");
		obsQuery.append(srcobsassocWhereClause);

		LocOOTask task = new LocOOTask();

		task.setProperties(properties);

		if (logger.getVerbosity() > 0)
			logger.write(String.format("Executing sql:%n%s%n",sourceQuery));

		long timer = System.nanoTime();

		Statement statement = inputSchema.getConnection().createStatement();
		ResultSet resultSet = statement.executeQuery(sourceQuery);

		task.setSources(new ArrayList<Source>());

		while (resultSet.next())
		{
			int columnIndex = 0;
			task.getSources().add(new Source(
					resultSet.getLong(++columnIndex),
					resultSet.getLong(++columnIndex),
					new GeoVector(resultSet.getDouble(++columnIndex),
							resultSet.getDouble(++columnIndex),
							resultSet.getDouble(++columnIndex),
							true),
							resultSet.getDouble(++columnIndex),
							-1., false));
		}
		resultSet.close();

		timer = System.nanoTime()-timer;

		if (logger.getVerbosity() > 0)
			logger.write(String.format("Query returned %d records in %s%n", 
					task.getSources().size(), GMPGlobals.ellapsedTime(timer*1e-9)));


		// now observations and receivers.

		if (logger.getVerbosity() > 0)
			logger.write(String.format("Executing sql:%n%s%n",obsQuery.toString()));

		timer = System.nanoTime();

		resultSet = statement.executeQuery(obsQuery.toString());

		long receiverId;
		int ondate, offdate;
		String sta, staname;

		HashMap<String, Receiver> receiversKB = new HashMap<String, Receiver>();

		task.setReceivers(new HashMap<Long, Receiver>());

		task.setObservations(new HashMap<Long, ArrayList<LocOOObservation>> (task.getSources().size()*5));

		int nObs = 0, nResults=0;
		long nextId = 1;
		double time, deltim, az, delaz, slow, delslo;

		while (resultSet.next())
		{
			++nResults;
			Receiver receiver = null;
			receiverId = resultSet.getLong(3); 
			sta = resultSet.getString(4);  
			ondate = resultSet.getInt(8);
			offdate = resultSet.getInt(9);
			staname = resultSet.getString(10);

			GeoVector receiverPosition = new GeoVector(
					resultSet.getDouble(5), 
					resultSet.getDouble(6), 
					-resultSet.getDouble(7), 
					true);

			if (receiverId < 0)
			{
				String sta_ondate = String.format("%s_%d", sta, ondate);


				receiver = receiversKB.get(sta_ondate);
				if (receiver == null)
				{
					receiverId = nextId++;
					receiver = new Receiver(sta, ondate, offdate,
							resultSet.getDouble(5), 
							resultSet.getDouble(6), 
							resultSet.getDouble(7), 
							staname, "-", "-", 0., 0.);
					receiver.setReceiverId(receiverId);
					receiversKB.put(sta_ondate, receiver);
					task.getReceivers().put(receiverId, receiver);
				}
			}
			else
			{
				receiver = task.getReceivers().get(receiverId);
				if (receiver == null)
				{
					receiver = new Receiver(
							receiverId, 
							sta, 
							GMTFormat.getEpochTime(ondate), 
							GMTFormat.getOffTime(offdate), 
							receiverPosition
							);
					task.getReceivers().put(receiverId, receiver);
				}

			}

			try
			{
				// get time,az, slowness
				time = resultSet.getDouble(12);
				deltim = resultSet.getDouble(13);
				az = resultSet.getDouble(15);
				delaz = resultSet.getDouble(16);
				slow = resultSet.getDouble(18);
				delslo = resultSet.getDouble(19);
				// ensure that na_values are all Globals.NA_VALUE
				// and convert degrees to radians
				if (time == ArrivalExtended.TIME_NA)
					time = Globals.NA_VALUE;
				if (deltim == ArrivalExtended.DELTIM_NA)
					deltim = Globals.NA_VALUE;

				if (az == ArrivalExtended.AZIMUTH_NA)
					az = Globals.NA_VALUE;
				if (az != Globals.NA_VALUE)
					az = Math.toRadians(az);

				if (delaz == ArrivalExtended.DELAZ_NA)
					delaz = Globals.NA_VALUE;
				if (delaz != Globals.NA_VALUE)
					delaz = Math.toRadians(delaz);

				if (slow == ArrivalExtended.SLOW_NA)
					slow = Globals.NA_VALUE;
				if (slow != Globals.NA_VALUE)
					slow = Math.toDegrees(slow);

				if (delslo == ArrivalExtended.DELSLO_NA)
					delslo = Globals.NA_VALUE;
				if (delslo != Globals.NA_VALUE)
					delslo = Math.toDegrees(delslo);

				LocOOObservation obs = new LocOOObservation(
						resultSet.getLong(2),
						receiver.getReceiverId(),
						resultSet.getString(11),
						time, deltim, resultSet.getString(14).charAt(0),
						az, delaz, resultSet.getString(17).charAt(0),
						slow, delslo, resultSet.getString(20).charAt(0)
						);

				Long orid = resultSet.getLong(1);
				ArrayList<LocOOObservation> observations = task.getObservations().get(orid);
				if (observations == null)
				{
					observations = new ArrayList<LocOOObservation>();
					task.getObservations().put(orid, observations);
				}
				observations.add(obs);
				++nObs;

			}
			catch (Exception e)
			{
				errorlog.writeln();
				errorlog.writeln(e);
			}

		}

		resultSet.close();
		statement.close();

		timer = System.nanoTime()-timer;

		if (logger.getVerbosity() > 0)
			logger.write(String.format("Query returned %d records in %s%n", 
					nResults, GMPGlobals.ellapsedTime(timer*1e-9)));

		if (logger.getVerbosity() > 0)
			logger.write(String.format("Discovered %d sources, %d receivers and %d observations.%n", 
					task.getSources().size(), task.getReceivers().size(), nObs));

		return task;
	}

	/**
	 * 
	 * @param schema
	 * @param nextSourceId 
	 * @param results
	 * @throws Exception 
	 */
	@Override
	public void writeTaskResult(LocOOTaskResult results) 
					throws Exception
	{
		if (outputSchema == null || results.getResults().isEmpty())
			return;

		if (!properties.getBoolean("dbOutputConstantOrid", false))
			for (LocOOResult result : results.getResults())
				//if (result.isValid())
					result.setSourceId(nextSourceId.getAndIncrement());

		OriginExtended.writeOriginExtendeds(results.getOutputOrigins(), outputSchema, true);
	}

	/**
	 * Retrieve the next unused Id from the specified database table.  If the schema does 
	 * not have have a table of the specified type, or if the specified table type is not
	 * an idowner table, returns null.
	 * @param tableType must be one of origin, source, prediction
	 * @return next id
	 * @throws SQLException 
	 */
	public AtomicLong getNextId(DBTableTypes tableType) throws SQLException 
	{
		String table = outputSchema.getTableName(tableType.toString());
		if (table == null)
			return null;

		String id;
		switch (tableType)
		{
		case ORIGIN:
			id = "orid";
			break;
		case PREDICTION:
			id = "predictionid";
			break;
		case SOURCE:
			id = "sourceid";
			break;
		default:
			return null;
		}

		Statement statement = outputSchema.getConnection().createStatement();
		if (logger.getVerbosity() > 0)
			logger.write("Executing query "+String.format("select max(%s) from %s%n", id, table));
		ResultSet result = statement.executeQuery(String.format("select max(%s) from %s", id, table));
		AtomicLong nextId = null;
		if (result.next())
			nextId = new AtomicLong(result.getLong(1));
		else
			nextId = new AtomicLong();
		
		nextId.incrementAndGet();

		result.close();
		statement.close();
		
		if (logger.getVerbosity() > 0)
			logger.write(String.format("next %s = %d%n%n", id, nextId.get()));

		return nextId;
	}

	/**
	 * Map from sta/phase -> double[tt, az, sh] master event corrections;
	 */
	public HashMap<String, double[]> getMasterEventCorrections() {
		return masterEventCorrections;
	}

	@Override
	public void close() throws Exception {
		// do nothing
	}

}
