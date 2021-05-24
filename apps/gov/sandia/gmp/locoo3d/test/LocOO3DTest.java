//- ****************************************************************************
//-
//- Copyright 2009 Sandia Corporation. Under the terms of Contract
//- DE-AC04-94AL85000 with Sandia Corporation, the U.S. Government
//- retains certain rights in this software.
//-
//- BSD Open Source License.
//- All rights reserved.
//-
//- Redistribution and use in source and binary forms, with or without
//- modification, are permitted provided that the following conditions are met:
//-
//-    * Redistributions of source code must retain the above copyright notice,
//-      this list of conditions and the following disclaimer.
//-    * Redistributions in binary form must reproduce the above copyright
//-      notice, this list of conditions and the following disclaimer in the
//-      documentation and/or other materials provided with the distribution.
//-    * Neither the name of Sandia National Laboratories nor the names of its
//-      contributors may be used to endorse or promote products derived from
//-      this software without specific prior written permission.
//-
//- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
//- AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
//- IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
//- ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
//- LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
//- CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
//- SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
//- INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
//- CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
//- ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
//- POSSIBILITY OF SUCH DAMAGE.
//-
//- ****************************************************************************

package gov.sandia.gmp.locoo3d.test;

import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;
import gov.sandia.gmp.baseobjects.PropertiesPlusGMP;
import gov.sandia.gmp.baseobjects.Receiver;
import gov.sandia.gmp.baseobjects.Source;
import gov.sandia.gmp.baseobjects.geovector.GeoVector;
import gov.sandia.gmp.baseobjects.globals.IMSNetwork;
import gov.sandia.gmp.locoo3d.LocOO;
import gov.sandia.gmp.locoo3d.LocOOObservation;
import gov.sandia.gmp.locoo3d.LocOOResult;
import gov.sandia.gmp.locoo3d.LocOOTask;
import gov.sandia.gmp.locoo3d.LocOOTaskResult;
import gov.sandia.gmp.util.exceptions.GMPException;
import gov.sandia.gmp.util.globals.GMTFormat;
import gov.sandia.gnem.dbtabledefs.nnsa_kb_core.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class LocOO3DTest
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		try
		{
			if (args.length == 1)
				new LocOO3DTest().locate(args[0]);
			else if (args.length == 2 && args[0].equalsIgnoreCase(("locate")))
				new LocOO3DTest().locate(args[1]);
			else if (args.length == 2 && args[0].equalsIgnoreCase(("locateLogFile")))
				new LocOO3DTest().locateLogFile(args[1]);

			System.out.println("Done.");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	protected void locate(String propertyFile) throws Exception
	{
		PropertiesPlusGMP properties = new PropertiesPlusGMP(new File(propertyFile));

		Set<Origin> origins = Origin.readOrigins(properties.getFile("inputOriginFile"));
		System.out.printf("Loaded %4d origins%n", origins.size());

		Set<Assoc> assocs = Assoc.readAssocs(properties.getFile("inputAssocFile"));
		System.out.printf("Loaded %4d assocs%n", assocs.size());

		Set<Arrival> arrivals = Arrival.readArrivals(properties.getFile("inputArrivalFile"));
		System.out.printf("Loaded %4d arrivals%n", arrivals.size());

		Set<Site> sites = Site.readSites(properties.getFile("inputSiteFile"));
		System.out.printf("Loaded %4d sites%n", sites.size());

		String dbOutputTableTypes = "";
		File originOutputFile = properties.getFile("originOutputFile");
		if (originOutputFile != null)
			dbOutputTableTypes += "origin";

		File origerrOutputFile = properties.getFile("origerrOutputFile");
		if (origerrOutputFile != null)
			dbOutputTableTypes += ", origerr";

		File azgapOutputFile = properties.getFile("azgapOutputFile");
		if (azgapOutputFile != null)
			dbOutputTableTypes += ", azgap";

		File assocOutputFile = properties.getFile("assocOutputFile");
		if (assocOutputFile != null)
			dbOutputTableTypes += ", assoc";

		properties.setProperty("dbOutputTableTypes", dbOutputTableTypes);

		System.out.println("Build LocooTask");

		// create a locoo task object with properties, sources, observations and receivers.
		LocOOTask task = new LocOOTask(properties, origins, assocs, arrivals, sites);

		System.out.println("locate");

		LocOOTaskResult locooResult = new LocOO().locateNonStatic(task);

		System.out.println("Log:");
		System.out.println(locooResult.getLog());

		int verbosity = properties.getInt("io_verbosity", 0);
		if (verbosity > 0)
		{
			File logFile = properties.getFile("io_log_file");
			if (logFile != null)
			{
				BufferedWriter output = new BufferedWriter(new FileWriter(logFile));
				output.write(locooResult.getLog().toString());
				output.close();
			}
		}
		File errFile = properties.getFile("io_error_file");
		if (errFile != null)
		{
			BufferedWriter output = new BufferedWriter(new FileWriter(errFile));
			output.write(locooResult.getErrorlog().toString());
			output.close();
		}


		System.out.println();
		System.out.println();

		System.out.println("ErrorLog:");
		System.out.println(locooResult.getErrorlog());

		BufferedWriter output = null;

		if (originOutputFile != null)
		{
			output = new BufferedWriter(new FileWriter(originOutputFile));
			for (LocOOResult result : locooResult.getResults())
				result.getOriginRow().writeln(output);
			output.close();
		}

		if (origerrOutputFile != null)
		{
			output = new BufferedWriter(new FileWriter(origerrOutputFile));
			for (LocOOResult result : locooResult.getResults())
				result.getOriginRow().getOrigerr().writeln(output);
			output.close();
		}

		if (assocOutputFile != null)
		{
			output = new BufferedWriter(new FileWriter(assocOutputFile));
			for (LocOOResult result : locooResult.getResults())
				for (Assoc assoc : result.getOriginRow().getAssocs())
					assoc.writeln(output);
			output.close();
		}

		if (azgapOutputFile != null)
		{
			output = new BufferedWriter(new FileWriter(azgapOutputFile));
			for (LocOOResult result : locooResult.getResults())
				result.getOriginRow().getAzgap().writeln(output);
			output.close();
		}
	}


	protected void locateLogFile(String propertyFile) throws Exception
	{
		PropertiesPlusGMP properties = new PropertiesPlusGMP(new File(propertyFile));

		if (!properties.containsKey("inputLogFile"))
			throw new IOException(String.format("%nproperties file does not specify property 'inputLogFile'%n%s%n",
					propertyFile));

		Scanner scnr, input = new Scanner(properties.getFile("inputLogFile"));

		String line = input.nextLine();
		while (!line.startsWith("Properties from file"))
			line = input.nextLine();

		line = input.nextLine();
		while (!line.isEmpty())
		{
			String[] keyValue = line.split("=");
			if (keyValue.length == 2 && !properties.containsKey(keyValue[0].trim()))
				properties.setProperty(keyValue[0].trim(), keyValue[1].trim());

			line = input.nextLine();
		}

		while (!line.startsWith("Input location:"))
			line = input.nextLine();

		while (!line.contains("Orid      Evid"))
			line = input.nextLine();

		Origin origin = new Origin();
		scnr = new Scanner(input.nextLine());
		origin.setOrid(scnr.nextLong());
		origin.setEvid(scnr.nextLong());
		origin.setLat(scnr.nextDouble());
		origin.setLon(scnr.nextDouble());
		origin.setDepth(scnr.nextDouble());
		origin.setTime(scnr.nextDouble());

		ArrayList<Origin> origins = new ArrayList<Origin>();
		origins.add(origin);

		while (!line.startsWith("Sta      OnDate   OffDate      Lat         Lon       Elev    StaName"))
			line = input.nextLine();

		line = input.nextLine();
		HashSet<Site> sites = new HashSet<Site>();
		while (!line.isEmpty())
		{
			Site site = new Site();
			scnr = new Scanner(line);
			site.setSta(scnr.next());
			site.setOndate(scnr.nextLong());
			site.setOffdate(scnr.nextLong());
			site.setLat(scnr.nextDouble());
			site.setLon(scnr.nextDouble());
			site.setElev(scnr.nextDouble());
			site.setStaname(scnr.nextLine());
			sites.add(site);
			line = input.nextLine();
		}

		while (!line.contains("Arid  Sta    Phase   Typ Def      Dist          Obs      Obs_err    Predictor"))
			line=input.nextLine();

		ArrayList<Assoc> assocs = new ArrayList<Assoc>();
		ArrayList<Arrival> arrivals = new ArrayList<Arrival>();
		line = input.nextLine();
		while (!line.isEmpty())
		{
			Arrival arrival = new Arrival();
			Assoc assoc = new Assoc();
			assoc.setOrid(origin.getOrid());

			scnr = new Scanner(line);
			arrival.setArid(scnr.nextLong());
			assoc.setArid(arrival.getArid());
			arrival.setSta(scnr.next());
			assoc.setSta(arrival.getSta());
			arrival.setIphase(scnr.next());
			assoc.setPhase(arrival.getIphase());
			String type = scnr.next().toLowerCase();
			String defining = scnr.next();
			if (defining.equals("-")) defining = "n";
			assoc.setDelta(scnr.nextDouble());
			double obs = scnr.nextDouble();
			double obs_err = scnr.nextDouble();
			if (type.equals("tt"))
			{
				arrival.setTime(origin.getTime()+obs);
				arrival.setDeltim(obs_err);
				assoc.setTimedef(defining);
			}
			else if (type.equals("az"))
			{
				arrival.setAzimuth(obs);
				arrival.setDelaz(obs_err);
				assoc.setAzdef(defining);
			}
			if (type.equals("sh"))
			{
				arrival.setSlow(obs);
				arrival.setDelslo(obs_err);
				assoc.setSlodef(defining);
			}
			arrivals.add(arrival);
			assocs.add(assoc);
			line = input.nextLine();

		}
		scnr.close();
		input.close();

		// create a locoo task object with properties, sources, observations and receivers.
		LocOOTask task = new LocOOTask(properties, origins, assocs, arrivals, sites);

		LocOOTaskResult locooResult = new LocOO().locateNonStatic(task);

		System.out.println(locooResult.getLog());

		System.out.println("ErrorLog:");
		System.out.println(locooResult.getErrorlog());

		System.out.printf("Properties:%n%s%n", properties.getRequestedPropertiesString(true));
	}


	//	protected void locate(String[] args) throws Exception
	//	{
	//		PropertiesPlusGMP properties = new PropertiesPlusGMP(new File(args[0]));
	//		
	//		Scanner input = null;
	//		
	//		ArrayList<Source> sources = new ArrayList<Source>();
	//		input = new Scanner(properties.getFile("inputOriginFile"));
	//		while (input.hasNext())
	//		{
	//			OriginRow originRow = new OriginRow(input);
	//			sources.add(new Source(originRow));
	//		}
	//		input.close();
	//		
	//		System.out.printf("Loaded %4d origins%n", sources.size());
	//		
	//		input = new Scanner(properties.getFile("inputSiteFile"));
	//		HashMap<String, HashSet<Receiver>> rcvrs = new HashMap<String, HashSet<Receiver>>();
	//		HashMap<Long, Receiver> receivers = new HashMap<Long, Receiver>();
	//		int n=0;
	//		while (input.hasNext())
	//		{
	//			SiteRow siteRow = new SiteRow(input);
	//			Receiver r = new Receiver(siteRow).setReceiverId(n++);
	//			HashSet<Receiver> list = rcvrs.get(r.getSta());
	//			if (list == null)
	//			{
	//				list = new HashSet<Receiver>();
	//				rcvrs.put(r.getSta(), list);
	//			}
	//			list.add(r);
	//			receivers.put(r.getReceiverId(), r);
	//		}
	//		input.close();
	//
	//		System.out.printf("Loaded %4d sites%n", receivers.size());
	//		
	//		input = new Scanner(properties.getFile("inputArrivalFile"));
	//		HashMap<Long, ArrivalRow> arrivals = new HashMap<Long, ArrivalRow>();
	//		while (input.hasNext())
	//		{
	//			ArrivalRow arrival = new ArrivalRow(input);
	//			arrivals.put(arrival.getArid(), arrival);
	//		}
	//		input.close();
	//		
	//		System.out.printf("Loaded %4d arrivals%n", arrivals.size());
	//		
	//		HashSet<String> invalidPhases = new HashSet<String>();
	//		if (properties.containsKey("invalidPhases"))
	//		{
	//			String[] p = properties.getProperty("invalidPhases")
	//					.replaceAll(",", " ").replaceAll("  ", " ").split(" ");
	//			for (String s : p)
	//			{
	//				s = s.trim();
	//				if (s.length() > 0)
	//					invalidPhases.add(s);
	//			}
	//		}
	//		
	//		input = new Scanner(properties.getFile("inputAssocFile"));
	//		ArrayList<AssocRow> assocs = new ArrayList<AssocRow>();
	//		while (input.hasNext())
	//		{
	//			AssocRow assoc = new AssocRow(input);
	//			if (!invalidPhases.contains(assoc.getPhase()))
	//				assocs.add(assoc);
	//		}
	//		input.close();
	//
	//		System.out.printf("Loaded %4d assocs%n%n", assocs.size());
	//		
	//		// instantiate a list of observations (basically assoc + arrival).
	//		// make a map from orid to a list of observations.
	//		HashMap<Long, ArrayList<LocOOObservation>> observations = new HashMap<Long, ArrayList<LocOOObservation>>();
	//		
	//		for (AssocRow assoc : assocs)
	//		{
	//			ArrivalRow arrival = arrivals.get(assoc.getArid());
	//			Receiver rcvr = null;
	//			Set<Receiver> rcvrSet = rcvrs.get(arrival.getSta());
	//			if (rcvrSet != null)
	//				for (Receiver r : rcvrSet)
	//					if (arrival.getJdate() >= r.getOndate() && arrival.getJdate() <= r.getOffdate())
	//					{
	//						rcvr = r;
	//						break;
	//					}
	//			if (rcvr == null)
	//				continue;
	//			
	//			SeismicPhase phase = SeismicPhase.valueOf(assoc.getPhase());
	//			
	//			LocOOObservation obs = new LocOOObservation(assoc.getArid(), 
	//					rcvr.getReceiverId(), phase, 
	//					arrival.getTime(), arrival.getDeltim(), assoc.getTimedef().charAt(0), 
	//					arrival.getAzimuth(), arrival.getDelaz(), assoc.getAzdef().charAt(0), 
	//					arrival.getSlow(), arrival.getDelslo(), assoc.getSlodef().charAt(0));
	//			
	//			ArrayList<LocOOObservation> list = observations.get(assoc.getOrid());
	//			if (list == null)
	//			{
	//				list = new ArrayList<LocOOObservation>();
	//				observations.put(assoc.getOrid(), list);
	//			}
	//			list.add(obs);
	//		}
	//		
	//		String dbOutputTableTypes = "";
	//		File originOutputFile = properties.getFile("originOutputFile");
	//		if (originOutputFile != null)
	//			dbOutputTableTypes += "origin";
	//
	//		File origerrOutputFile = properties.getFile("origerrOutputFile");
	//		if (origerrOutputFile != null)
	//			dbOutputTableTypes += ", origerr";
	//
	//		File azgapOutputFile = properties.getFile("azgapOutputFile");
	//		if (azgapOutputFile != null)
	//			dbOutputTableTypes += ", azgap";
	//
	//		File assocOutputFile = properties.getFile("assocOutputFile");
	//		if (assocOutputFile != null)
	//			dbOutputTableTypes += ", assoc";
	//
	//		properties.setProperty("dbOutputTableTypes", dbOutputTableTypes);
	//		
	//		System.out.println("Build LocooTask");
	//		
	//		// create a locoo task object with properties, sources, observations and receivers.
	//		LocOOTask task = new LocOOTask(properties, sources, observations, receivers);
	//
	//		System.out.println("locate");
	//		
	//		LocOOTaskResult locooResult = new LocOO().locateNonStatic(task);
	//		
	//		System.out.println("Log:");
	//		System.out.println(locooResult.getLog());
	//		
	//		System.out.println();
	//		System.out.println();
	//		
	//		System.out.println("ErrorLog:");
	//		System.out.println(locooResult.getErrorlog());
	//		
	//		BufferedWriter output = null;
	//		
	//		if (originOutputFile != null)
	//		{
	//			output = new BufferedWriter(new FileWriter(originOutputFile));
	//			for (LocOOResult result : locooResult.getResults())
	//				result.getOriginRow().writeAscii(output);
	//			output.close();
	//		}
	//		
	//		if (origerrOutputFile != null)
	//		{
	//			output = new BufferedWriter(new FileWriter(origerrOutputFile));
	//			for (LocOOResult result : locooResult.getResults())
	//				result.getOrigErrRow().writeAscii(output);
	//			output.close();
	//		}
	//		
	//		if (assocOutputFile != null)
	//		{
	//			output = new BufferedWriter(new FileWriter(assocOutputFile));
	//			for (LocOOResult result : locooResult.getResults())
	//				for (AssocRow assoc : result.getAssocRows())
	//					assoc.writeAscii(output);
	//			output.close();
	//		}
	//		
	//		if (azgapOutputFile != null)
	//		{
	//			output = new BufferedWriter(new FileWriter(azgapOutputFile));
	//			for (LocOOResult result : locooResult.getResults())
	//				result.getAzgapRow().writeAscii(output);
	//			output.close();
	//		}
	//		
	//		System.out.println("Done.");
	//	}


	protected void locooLibrary(String[] args) throws GMPException, IOException
	{
		// make a properties object just for locoo
		PropertiesPlusGMP locooProperties = new PropertiesPlusGMP();

		locooProperties.setProperty("seismicBaseData", "k:\\seismicBaseData");

		locooProperties.setProperty("loc_predictor_type", "tauptoolkit");
		locooProperties.setProperty("taupToolkitModel", "ak135");

		locooProperties.setProperty("tauptoolkitUncertaintyDirectory", "<property:seismicBaseData>");
		locooProperties.setProperty("tauptoolkitUncertaintyModel", "ak135");
		locooProperties.setProperty("tauptoolkitEllipticityCorrectionsDirectory", "<property:seismicBaseData>/el/ak135");

		locooProperties.setProperty("dbOutputConstantOrid", "true");
		locooProperties.setProperty("dbOutputTableTypes", "origin, origerr, assoc");

		locooProperties.setProperty("tauptoolkitUncertaintyType", "DistanceDependent");

		locooProperties.setProperty("tauptoolkitUncertaintyDirectory", "<property:seismicBaseData>");
		locooProperties.setProperty("tauptoolkitUncertaintyModel", "ak135");

		locooProperties.setProperty("gen_fix_depth", true);

		locooProperties.setProperty("io_verbosity", "4");
		locooProperties.setProperty("io_iteration_table", true);
		locooProperties.setProperty("io_observation_tables", "2");

		//locooProperties.setProperty("maxProcessors", 1);

		HashSet<Receiver> receiverList = new HashSet<Receiver>();
		receiverList.addAll(IMSNetwork.primary.getReceivers());
		receiverList.addAll(IMSNetwork.auxiliary.getReceivers());

		long receiverId = 0;
		for (Receiver receiver : receiverList)
			receiver.setReceiverId(receiverId++);

		HashMap<String, Long> rmap = new HashMap<String, Long>();
		for (Receiver r : receiverList)
			rmap.put(r.getSta(), r.getReceiverId());

		HashMap<Long, Receiver> receivers = new HashMap<Long, Receiver>();
		for (Receiver r : receiverList)
			receivers.put(r.getReceiverId(), r);

		// make an array of sources
		ArrayList<Source> sources = new ArrayList<Source>();

		sources.add(new Source(1562899L, -1L, new GeoVector(-23.155209701602, -67.615115647119, 107.900078665808, true), 1077789481.0736, 25));

		// hard code some data extracted from the database.
		//
		//      ARID STA    PHASE          TIME     DELTIM T    AZIMUTH      DELAZ A       SLOW     DELSLO S
		//---------- ------ -------- ---------- ---------- - ---------- ---------- - ---------- ---------- -
		String data =   
				"19861932   DBIC   P   1077790129.5     1   d   204.99    25.62   n    7.83   3.47   n " +       
						"22475590   ESDC   P   1077790231.137   1   d   130.36     4.73   n   17.44   1.44   n " +
						"19861936   LBTB   P   1077790216.425   1   d   257.54    31.04   n    6.41   3.43   n " +
						"19861935   FLAR   P   1077790213.25    1   d   157.78     9.15   n    7.53   1.2    n " +
						"19861934   PDAR   P   1077790177.25    1   d   134.04    19.92   n    6.57   2.27   n " +
						"19861931   TXAR   P   1077790094.15    1   d   144.56    12.02   n    7.75   1.62   n " +
						"19861933   VNDA   P   1077790173.025   1   d    94.82    180     n    1.56   3.74   n ";

		Scanner scanner = new Scanner(data);

		// instantiate a list of observations (basically assoc + arrival).
		ArrayList<LocOOObservation> obs = new ArrayList<LocOOObservation>();
		while (scanner.hasNext())
		{
			long arid = scanner.nextLong();
			String sta = scanner.next();
			String phase = scanner.next();
			double time = scanner.nextDouble();
			double deltim = scanner.nextDouble();
			char timedef = scanner.next().charAt(0);
			double az = scanner.nextDouble();
			double delaz = scanner.nextDouble();
			char azdef = scanner.next().charAt(0);
			double slow = scanner.nextDouble();
			double delslo = scanner.nextDouble();
			char slodef = scanner.next().charAt(0);

			if (rmap.containsKey(sta))
			{
				long rcvrId = rmap.get(sta);

				obs.add(new LocOOObservation(
						arid, rcvrId, phase, 
						time, deltim, timedef,
						toRadians(az), toRadians(delaz), azdef,
						toDegrees(slow), toDegrees(delslo), slodef));
			}
		}

		// make a map from orid to a list of observations.
		HashMap<Long, ArrayList<LocOOObservation>> observations = new HashMap<Long, ArrayList<LocOOObservation>>();

		// add the link from our only orid to the list of observations.  We could have constructed a bunch of
		// sources linked to separate lists of observations and added them to this map as well.
		observations.put(sources.get(0).getSourceId(), obs);

		// create a locoo task object with properties, sources, observations and receivers.
		LocOOTask task = new LocOOTask(locooProperties, sources, observations, receivers);

		LocOOTaskResult locooResult = null;
		for (int i=0; i<1; ++i)
			// locate the event.  
			locooResult = new LocOO().locateNonStatic(task);

		System.out.println("Log:");
		System.out.println(locooResult.getLog());

		System.out.println();
		System.out.println();

		System.out.println("ErrorLog:");
		System.out.println(locooResult.getErrorlog());

		System.out.println("Done.");

	}

	protected void libcorrTest(String[] args) throws GMPException, IOException
	{
		boolean useLibcorr = true;

		// make a properties object just for locoo
		PropertiesPlusGMP locooProperties = new PropertiesPlusGMP();

		// set a bunch of properties here.
		locooProperties.setProperty("libcorrRoot", args[0]);

		locooProperties.setProperty("seismicBaseData", args[1]);

		locooProperties.setProperty("loc_predictor_type", "tauptoolkit");
		locooProperties.setProperty("taupToolkitModel", "ak135");

		locooProperties.setProperty("tauptoolkitUncertaintyDirectory", "<property:seismicBaseData>");
		locooProperties.setProperty("tauptoolkitUncertaintyModel", "ak135");
		locooProperties.setProperty("tauptoolkitEllipticityCorrectionsDirectory", "<property:seismicBaseData>/el/ak135");
		locooProperties.setProperty("dbOutputConstantOrid", "true");
		locooProperties.setProperty("dbOutputTableTypes", "origin, origerr, assoc");

		if (useLibcorr)
			locooProperties.setProperty("tauptoolkitPathCorrectionsType","libcorr");

		locooProperties.setProperty("tauptoolkitLibCorrPreloadModels",false);

		locooProperties.setProperty("tauptoolkitUsePathCorrectionsInDerivatives",false);

		locooProperties.setProperty("tauptoolkitLibCorrPathCorrectionsRoot", "<property:libcorrRoot>");
		locooProperties.setProperty("tauptoolkitLibCorrPathCorrectionsRelativeGridPath","../../tess");

		if (useLibcorr)
			locooProperties.setProperty("tauptoolkitUncertaintyType", "hierarchical");
		else
			locooProperties.setProperty("tauptoolkitUncertaintyType", "DistanceDependent");

		locooProperties.setProperty("tauptoolkitUncertaintyDirectory", "<property:seismicBaseData>");
		locooProperties.setProperty("tauptoolkitUncertaintyModel", "ak135");

		locooProperties.setProperty("tauptoolkitLibCorrUncertaintyRoot", "<property:libcorrRoot>");
		locooProperties.setProperty("tauptoolkitLibCorrUncertaintyRelativeGridPath", "../../tess");

		locooProperties.setProperty("use_tt_path_corrections",true);

		//locooProperties.setProperty("gen_fix_depth", true);

		locooProperties.setProperty("io_verbosity", "4");
		locooProperties.setProperty("io_iteration_table", true);
		locooProperties.setProperty("io_observation_tables", "2");

		locooProperties.setProperty("useSimplex", true);

		//locooProperties.setProperty("maxProcessors", 1);


		locooProperties.setProperty("grid_output_file_name", "./LocOOTesting/locoo_grid_junk.vtk");
		locooProperties.setProperty("grid_origin_source", "hypocenter");

		locooProperties.setProperty("grid_map_nwidth",  11);
		locooProperties.setProperty("grid_map_nheight", 11);
		locooProperties.setProperty("grid_map_ndepth",  11);

		locooProperties.setProperty("grid_map_width", 120);
		locooProperties.setProperty("grid_map_height", 100);
		locooProperties.setProperty("grid_map_depth_range", "0 150");

		locooProperties.setProperty("grid_units", "km");

		locooProperties.setProperty("grid_output_file_format", "vtk");

		HashSet<Receiver> receiverList = new HashSet<Receiver>();

		Scanner supportMap = new Scanner(new File(locooProperties.getFile(
				"tauptoolkitLibCorrUncertaintyRoot"), "_supportMap.txt"));
		supportMap.nextLine();supportMap.nextLine();supportMap.nextLine();
		while (supportMap.hasNext())
		{
			supportMap.next();  // skip the file name
			String sta = supportMap.next();
			double lat = supportMap.nextDouble();
			double lon = supportMap.nextDouble();
			double elev = supportMap.nextDouble();
			int ondate = GMTFormat.getJDate(supportMap.nextDouble());
			int offdate = GMTFormat.getJDate(supportMap.nextDouble());
			supportMap.nextLine();

			Receiver rcv = new Receiver(sta, ondate, offdate, lat, lon, elev, "-", "-", sta, 0., 0.);
			receiverList.add(rcv);
		}

		HashMap<String, Long> rmap = new HashMap<String, Long>();
		for (Receiver r : receiverList)
			rmap.put(r.getSta(), r.getReceiverId());

		HashMap<Long, Receiver> receivers = new HashMap<Long, Receiver>();
		for (Receiver r : receiverList)
			receivers.put(r.getReceiverId(), r);

		// make an array of sources
		ArrayList<Source> sources = new ArrayList<Source>();

		sources.add(new Source(1562899L, -1L, new GeoVector(-23.155209701602, -67.615115647119, 107.900078665808, true), 1077789481.0736, 25));

		// hard code some data extracted from the database.
		//
		//      ARID STA    PHASE          TIME     DELTIM T    AZIMUTH      DELAZ A       SLOW     DELSLO S
		//---------- ------ -------- ---------- ---------- - ---------- ---------- - ---------- ---------- -
		String data =   
				"19861932   DBIC   P   1077790129.5     1   d   204.99    25.62   n    7.83   3.47   n " +       
						"22475590   ESDC   P   1077790231.137   1   d   130.36     4.73   n   17.44   1.44   n " +
						"19861935   FLAR   P   1077790213.25    1   d   157.78     9.15   n    7.53   1.2    n " +
						"19861936   LBTB   P   1077790216.425   1   d   257.54    31.04   n    6.41   3.43   n " +
						"19861934   PDAR   P   1077790177.25    1   d   134.04    19.92   n    6.57   2.27   n " +
						"19861931   TXAR   P   1077790094.15    1   d   144.56    12.02   n    7.75   1.62   n " +
						"19861933   VNDA   P   1077790173.025   1   d    94.82    180     n    1.56   3.74   n ";

		Scanner scanner = new Scanner(data);

		// instantiate a list of observations (basically assoc + arrival).
		ArrayList<LocOOObservation> obs = new ArrayList<LocOOObservation>();
		while (scanner.hasNext())
		{
			long arid = scanner.nextLong();
			String sta = scanner.next();
			long rcvrId = rmap.get(sta);
			String phase = scanner.next();
			double time = scanner.nextDouble();
			double deltim = scanner.nextDouble();
			char timedef = scanner.next().charAt(0);
			double az = scanner.nextDouble();
			double delaz = scanner.nextDouble();
			char azdef = scanner.next().charAt(0);
			double slow = scanner.nextDouble();
			double delslo = scanner.nextDouble();
			char slodef = scanner.next().charAt(0);

			obs.add(new LocOOObservation(
					arid, rcvrId, phase, 
					time, deltim, timedef,
					toRadians(az), toRadians(delaz), azdef,
					toDegrees(slow), toDegrees(delslo), slodef));
		}

		// make a map from orid to a list of observations.
		HashMap<Long, ArrayList<LocOOObservation>> observations = new HashMap<Long, ArrayList<LocOOObservation>>();

		// add the link from our only orid to the list of observations.  We could have constructed a bunch of
		// sources linked to separate lists of observations and added them to this map as well.
		observations.put(sources.get(0).getSourceId(), obs);

		// create a locoo task object with properties, sources, observations and receivers.
		LocOOTask task = new LocOOTask(locooProperties, sources, observations, receivers);

		//		Profiler profiler = new Profiler(Thread.currentThread(), 1, "GeoTessPosition profile");
		//		
		//		profiler.setTopClass("gov.sandia.gmp.libcorr3dgmp.LibCorr3DGMP");
		//		
		//		profiler.setTopMethod("getPathCorrection");
		//		
		//		profiler.accumulateOn();

		LocOOTaskResult locooResult = null;
		for (int i=0; i<1; ++i)
			// locate the event.  
			locooResult = new LocOO().locateNonStatic(task);

		//		profiler.accumulateOff();
		//		
		//		profiler.printAccumulationString();


		System.out.println("Log:");
		System.out.println(locooResult.getLog());

		System.out.println();
		System.out.println();

		System.out.println("ErrorLog:");
		System.out.println(locooResult.getErrorlog());

		System.out.println("Done.");

	}

}
