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

import static gov.sandia.gmp.util.globals.Globals.NL;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;

import gov.sandia.gmp.baseobjects.PropertiesPlusGMP;
import gov.sandia.gmp.parallelutils.ParallelBroker;
import gov.sandia.gmp.parallelutils.ParallelBroker.ParallelMode;
import gov.sandia.gmp.parallelutils.ParallelResult;
import gov.sandia.gmp.predictorfactory.PredictorFactory;
import gov.sandia.gmp.util.containers.arraylist.ArrayListLong;
import gov.sandia.gmp.util.globals.GMTFormat;
import gov.sandia.gmp.util.globals.Globals;
import gov.sandia.gmp.util.globals.Utils;
import gov.sandia.gmp.util.logmanager.ScreenWriterOutput;
import gov.sandia.gmp.util.numerical.vector.EarthShape;
import gov.sandia.gmp.util.numerical.vector.VectorGeo;
import gov.sandia.gmp.util.profiler.ProfilerContent;

public class LocOO
{
	static public String getVersion() {
		return Utils.getVersion("locoo3d");
	}

	// 1.12.0 2020-10-29 sballar Added depth constraints imposed by
	// seismicity_depth_model.
	//
	// 1.11.1 2020-07-08 sballar Fixed bug in LibCorr3D which 
	// prevented supportedPhases to be applied properly.
	//
	// 1.11.0 2018-12-13
    // Added file based input output. Changed IODB_LocOO to DataLoaderOracle.
	// Added interface DataLoader
	// Added file loader .... DataLoaderFile
	// Added new required property to input file "dataLoaderType" which must
	// be set to either "file" or "oracle". If new DB types are added then
	// appropriate names will be added in the future.
	// Modified LocOO.java the change over to DataLoader prescription.
		
	// 1.10.2g 2017-05-022
	// fixed bug related to observation filters
	//
	// 1.10.2 2017-03-06
	// delivered 
	//
	// 1.10.1f 2017-01-12
	// this version uses bender version 4.x.x
	// Also implements the new SeismicBaseData class
	// that has tt and el models stored in the jar file.
	//
	// 1.10.1d 2016-03-02 2016-07-20  
	// This version still uses bender that depends on GeoModel
	//
	// 1.10.1 2016-03-01 fixed a few bugs.
	//
	// 1.10.0 2016-01-22 added master event relocation capability
	//
	// 1.9.5 2015-10-17 removed geomodel from dependency list
	// by implementing a topography model with geotess.
	// 2016-01-04 Implemented Flinn_Engdahl region codes
	// (origin.srn and origin.grn).
	//
	// 1.9.4 2015-10-14 First maven release	
	//
	// 1.9.3 2014-12-10 Added ability to apply multiplier and offset to 
	// tt model uncertainty in Bender.  
	// Improved performance of input sql statements.	
	//
	// 2014-12-03 Version 1.9.2
	// Updated version number to reflect changes in GeoModel
	// and Bender to suppot models that contain both 
	// PSLOWNESS and SSLOWNESS and P and S site terms.
	//
	// 2014-10-23 Version 1.9.1
	// Added dependence on dbtabledefs Schema object.
	// Also fixed issue where GeoTessPosition was rebelling
	// when attempted to change libcorr3d models on the fly
	// but the grid ids were different.
	//
	// 2014-10-01 Version 1.9.0
	// Added dependence on dbtabledefs and removed dependence
	// on dbutillib.
	//
	// 2014-06-07 Version 1.8.10
	// Changed Origerr.sdobs to be the standard deviation of
	// tt, az and sh weighted residuals.  
	// Also made all the objects in DBTableDefs serializable.
	//
	// 2014-05-30 Version 1.8.9
	// fixed a bug in LookupTablesGMP that caused NaN
	// values for tt predictions.
	//
	// 2014-01-27 Version 1.8.8
	// fixed a bug that having to do with correlated 
	// observations. 
	//
	// 2013-12-19 Version 1.8.7
	// fixed a bug that caused azimuth weighted residuals
	// to be incorrect, leading to erroneous locations
	// under rare circumstances.  Changes were in 
	// ObservationComponent.setPrediction()
	//
	// 2013-12-5 Version 1.8.6
	// Output assoc.wgt to the database.  Fixed bug that
	// happened when lat, lon, depth are fixed and only
	// time is free.  It was throwing exception because
	// covariance matrix was singular.  Fixed that.
	// When lat,lon,depth fixed and only time is free,
	// predictions will be computed in sequential mode
	// because simplex algorithm is not thread-safe.
	//
	// 2013-11-01 Version 1.8.5
	// Added parameter lookup2dTTModelUncertaintyScale
	// to LookupTablesDZ.  Also, significant changes
	// to how Predictor handles concurrency.  Before,
	// predictor always processed one prediction at a 
	// time in concurrent mode.  Now processes batches
	// of predictions in concurrent mode.
	//
	// 2013-09012 Version 1.8.4
	// Fixed bugs in LibCorr3DModels that allowed the
	// wrong model to be associated with a station.
	//
	// 2013-07-16 Version 1.8.3
	// Mods to read tt lookup tables and ellipticity
	// correction tables in earth_specs 
	// directory formats.
	//
	// 2013-07-01 Version 1.8.2
	// Preparation for testing
	//
	// 2013-03-17 Version 1.8.1
	// Bug fixes 
	//
	// 2013-03-08 Version 1.8.0
	// Added library LookupTableDZ which provide support
	// for 2D, distance-depth travel time lookup tables
	// ala saic mini-tables.
	//
	// 2013-01-18 version 1.7.4  
	// Fixed bug in tauptoolkit that prevented
	// property tauptoolkitPgVelocity from being applied.
	//
	// 2013-01-17 Fixed bug where azimuth residuals were
	// not being wrapped properly into range -180 to 180.
	//
	// 2013-01-16 Fixed bug where site elevation was not 
	// being converted to depth in DataLoaderOracle.
	//
	// 2012-10-19 version 1.7.0 (sballar)
	// - Implemented LibCorr3D
	//
	// 2011-07-08 version 1.6.0 (sballar)
	// - Implemented core phase renaming algorithm.  
	//   This allows locoo to rename P to PKP and vice versa
	//   if the residuals are dramatically improved by doing so.
	// - Also uses version of Bender that precomputes velocity 
	//   gradient information.
	// - Implemented Simplex algorith.
	//
	// 2011-06-27 version 1.5.0 (sballar)
	// Implements ttt_path_corrections as implemented in 
	// TaupToolkitWrapper.
	//
	// 2011-06-20 version 1.4.3 (sballar)
	// No real changes to locoo but changes to dependencies
	//
	// 2011-02-18 version 1.4.2 (sballar)
	// was terminating ungracefully for ill-posed problems
	// (covariance matrix was singular).  Improved robustness.
	// Also incorporates some bender improvements.
	//
	// 2011-01-20 version 1.4.1 (sballar)
	// fixed problem that caused incorrect results when
	// allow_big_residuals was set to false.
	//
	// 2010-11-24 version 1.4.0 (sballar)
	// reintroduced ability to use correlated observations
	//
	// 2010-11-12 version 1.3.2 (sballar)
	// fixed bug in TaupToolkitWrapper where PKPbc
	// was not handled properly.
	//
	// 2010-11-05 version 1.3.1 (sballar)
	// when writing set of results to db, and an oracle 
	// error happened, locoo was rolling back the entire
	// set of locations instead of just the one that 
	// caused the error.
	//
	// 2010-10-26 version 1.3.0 (sballar)
	// topography now obtained from a GeoModel file that is
	// separate from any of the predictors.
	//
	// 2010-10-25 version 1.2.7 (sballar)
	// Fixed bug in IODB that allowed more than 1000 orids in 
	// a single batch.  This caused a sql failure when oracle
	// tried to execute 'where orid in (...)'
	//
	// 2010-10-03 version 1.2.6 (sballar)
	// Fixed bug that prevented calculation with lat, lon, depth fixed
	// (only origin time free).
	//
	// 2010-07-08 version 1.2.4 (sballar)
	// 1. regurgitate properties at beginning of output text file.
	// 2. sort observation/prediction tables by source-receiver distance
	// 3. fixed bug where origin time date was not in GMT timezone
	//
	// 2010-07-07 version 1.2.3 (sballar)
	// no changes from 1.2.2.  
	//
	// 2010-07-06 version 1.2.2 (sballar)
	// fixed all the uncertainty related parameters to be 
	// consistent across all the different predictors.
	//
	// 2010_07_02 version 1.2.1 (sballar) 
	// fixed slbm uncertainty so that it will work with locoo3d
	//
	// 2010_06_30 (avencar)
	// added functionality for running in distributed mode
	// on the NRM cluster with JPPF
	//
	// 2010_03_04 version 1.1.0
	// ensured that if exception thrown in iodb output
	// it does not bring down the whole run.
	//
	// 2009_11_05 version 1.0.9
	// switched esaz and seaz in assoc table output
	// improved error handling.
	//
	// 2009_11_05 version 1.0.8 improved error handling.
	//
	// 2009-11-03 version 1.0.7 sent to lanl
	// added parameter dbOutputConstantOrid
	//

	private ScreenWriterOutput logger, errorlog;

	/**
	 * @param args
	 */
	static public void main(String[] args)
	{
		try
		{
			if (args.length != 1)
			{
				StringBuffer buf = new StringBuffer();
				System.out.print(String.format("LocOO version %s   %s%n%n", 
						LocOO.getVersion(), GMTFormat.localTime.format(new Date())));

				System.out.println("LocOO dependencies:");
				try {
					System.out.println(Utils.getDependencyVersions());
				} catch (IOException e) {
					for (String d : LocOO.getDependencies())
						System.out.println(d);
					System.out.println();
				}

				buf.append("Must specify property file as only command line argument.\n\n");
				for (String arg : args)
					buf.append(String.format("%s%n", arg));
				throw new LocOOException(buf.toString());
			}

			if (!args[0].toLowerCase().endsWith(".properties"))
				args[0] += ".properties";

			File propertyFile = new File(args[0]);
			if (!propertyFile.exists())
				throw new LocOOException("Property file "
						+ propertyFile.getCanonicalPath() + " does not exist");

			PropertiesPlusGMP properties = null;
			properties = new PropertiesPlusGMP(propertyFile);

			(new LocOO()).run(properties);
			System.exit(0);
		} 
		catch (Exception e)
		{
			e.printStackTrace();
		}

	}

	public void run(PropertiesPlusGMP properties)
	{
		long startTime = System.currentTimeMillis();
		ParallelBroker parallelBroker = null;
		long executionTime=0;
		int nSources = 0;
		
		VectorGeo.earthShape = EarthShape.valueOf(
				properties.getProperty("earthShape", "WGS84"));

		try
		{
			setupLoggers(properties);

			if (logger.getVerbosity() > 0)
			{
				if (properties.getPropertyFile() != null)
					logger.writeln("Properties from file "+properties.getPropertyFile().getCanonicalPath());
				else
					logger.writeln("Properties:");
				logger.writeln(properties.toString());
			}

			// test to ensure that alot of the files and directories that are 
			// specified in the properties file actually exist.
			//testFilesInProperties(properties);
			
			ParallelMode parallelMode = null;
			try {
				// see if the requested mode is supported by the current version of ParallelUtils.
				parallelMode = ParallelMode.valueOf(properties.getProperty("parallelMode", "sequential").toUpperCase());
			} catch (Exception e2) {
				throw new Exception("The version of ParallelUtils implemented in this version of LocOO3D "
						+ "does not support parallelMode = "+properties.getProperty("parallelMode", "sequential"));
			}

			parallelBroker = ParallelBroker.create(parallelMode);
			parallelBroker.setProperties(properties);

			if (logger.getVerbosity() > 0)
			{
				if (parallelMode == ParallelMode.SEQUENTIAL)
					logger.writeln("ParallelMode = sequential (locations computed in sequential mode, predictions in concurrent mode)");
				else if (parallelMode == ParallelMode.DISTRIBUTED || parallelMode == ParallelMode.DISTRIBUTED_FABRIC)
					logger.writeln("ParallelMode = distributed (locations computed in distributed mode, predictions in sequential mode)");
				else if (parallelMode == ParallelMode.CONCURRENT)
					logger.writeln("ParallelMode = concurrent (locations computed in conccurent mode, predictions in sequential mode)");
				else
					throw new LocOOException(String.format("parallelMode = %s but must be one of sequential, concurrent, or distributed.",
							parallelMode));

				logger.writeln("Using "
						+properties.getInt("maxProcessors", Runtime.getRuntime().availableProcessors())
						+" of "+Runtime.getRuntime().availableProcessors()
						+" available processors.");
			}

			LocOOTaskResult result;			

			// create data loader from property "dataLoaderType" and initialize
			DataLoader dataLoader = DataLoader.create(properties);
			dataLoader.initialize(properties, logger, errorlog);

			// a 2D ragged array of longs.  The first index spans the batches and the 
			// second spans the orids|sourceids in each batch.  Each batch represents one or more
			// sources to be located such that each batch has approximately  the same number
			// of defining observations in it.  Earlier batches will have fewer,larger, sources 
			// per batch and later batches will have more, smaller, sources per batch.
			ArrayList<ArrayListLong> batches = dataLoader.readTaskSourceIds();

			if (logger.getVerbosity() > 0)
				logger.writeln("Number of batches to submit: " + batches.size());

			nSources = 0;
			for (ArrayListLong batch : batches)
				nSources += batch.size();

			if (logger.getVerbosity() > 0)
				logger.writeln("Total number of events to process: " + nSources);

			// index over all the batches
			int index = 0;

			// in the while loop that follows, this is the number of batches that
			// will be submitted to the broker before any are retrieved from the broker.
			int queueSizeMax = properties.getInt("queueSizeMax", Runtime.getRuntime().availableProcessors()+1);

			if (logger.getVerbosity() > 0)
				logger.writeln("Parallel broker queue size: " + queueSizeMax);

			ProfilerContent predProfilerContent = null;
			long profilerSamplePeriod = properties.getInt("profilerSamplePeriod", -1);
			if (profilerSamplePeriod > 0)
				predProfilerContent = new ProfilerContent();

			// loop until all the batches have been submitted and no more batches
			// are still in the hands of the broker.
			while (index < batches.size())
			{
				// Build up a set of tasks consisting of at most queueSizeMax tasks,
				// and then submit them through the parallel broker.
				int queueSize = 0;
				List<LocOOTask> tasks = new ArrayList<LocOOTask>();

				if (logger.getVerbosity() > 0)
					logger.writeln("Building up task set of size <= " + queueSizeMax);

				int tasksSubmitted = 0;
				long numTasksPerSubmit = Long.MAX_VALUE;

				// If we have more than one client thread, then submit tasks in bunches
				// so that nodes can start processing tasks right away.
				if (parallelMode.equals("DISTRIBUTED_FABRIC"))
				{
					numTasksPerSubmit = 1000;
					logger.writeln("Num tasks per submit: " + numTasksPerSubmit);
				}
				else if (parallelBroker.getClientThreadPoolSize() > 1)
				{
					numTasksPerSubmit = (queueSizeMax / parallelBroker.getClientThreadPoolSize());
					if (numTasksPerSubmit < 1000) numTasksPerSubmit = 1000;	// minimum per bunch
					logger.writeln("Num tasks per submit: " + numTasksPerSubmit);
				}


				// Accumulate batches/tasks to submit to broker before waiting for results
				while (queueSize < queueSizeMax && index < batches.size()) 
				{
					tasks.add(dataLoader.readTaskObservations(batches.get(index++)));
					queueSize++;
					if (queueSize % 100 == 0 && logger.getVerbosity() > 0) 
						logger.writeln("Current task set size is: " + queueSize);

					if (queueSize % numTasksPerSubmit == 0) {	// Submit accumulated bunch
						parallelBroker.submit(tasks);
						tasksSubmitted += tasks.size();
						tasks = new ArrayList<LocOOTask>();
					}
				}

				if (logger.getVerbosity() > 0)
					logger.writeln("Submitting task set, and then waiting for results");

				// Submit last task set
				parallelBroker.submit(tasks);
				tasksSubmitted += tasks.size();
				int resultsCount = 0;

				// Wait for all results
				while (resultsCount < tasksSubmitted) 
				{
					long t0 = System.currentTimeMillis();

					// here is where all the location calculations are done.
					List<ParallelResult> results = parallelBroker.getResultsWait();

					executionTime += System.currentTimeMillis()-t0;

					for (ParallelResult r : results) 
					{
						result = (LocOOTaskResult)r;

						try 
						{
							// write the result to output db or files.
							dataLoader.writeTaskResult(result);
						}
						catch (Exception e)	{ 
							errorlog.writeln(e);
							e.printStackTrace();
						}

						// Output the log for each result
						if (result != null && result.getLog() != null && logger.getVerbosity() > 0)
							try {
								logger.write(result.getLog().toString());
								errorlog.write(result.getErrorlog().toString());
							} catch (Exception e1) {
								e1.printStackTrace();
							}

						// add profile results if defined
						ProfilerContent pc = result.getProfilerContent();
						if (pc != null) predProfilerContent.addProfilerContent(pc);
					}
					resultsCount++;
				}

				// output profiler content if defined

				if (predProfilerContent != null)
				{
					String s = NL + "    Prediction Profile Output ..." + NL;
					logger.write(s);
					logger.write(predProfilerContent.getAccumulationString("      "));
					logger.write(NL);
				}
			}
			
			dataLoader.close();
		} 
		catch (Exception ex)
		{
			if (errorlog != null)
				errorlog.writeln(ex);
			else 
				ex.printStackTrace();
		}
		finally
		{
			if (parallelBroker != null)
			{
				parallelBroker.close();
				parallelBroker = null;
			}
		}

		if (logger.getVerbosity() > 0)
		{

			logger.writeln();
			logger.writeln();
			logger.writeln(properties.getRequestedPropertiesString(true));

			logger.write(String.format("Time = %s%nElapsed time = %s%n",
					GMTFormat.localTime.format(new Date()), Globals.elapsedTime(startTime)));
			if (nSources > 0)
				logger.write(String.format("Execution time (sec) = %1.3f%nExecution time (sec/source) = %1.3f%n",
						executionTime*1e-3, executionTime*1e-3/nSources));
			else
				logger.write(String.format("Execution time (sec) = %1.3f%nnSources = 0%n",
						executionTime*1e-3));	

			logger.write(String.format("Done.%n"));
		}
	}

	static public LocOOTaskResult locate(LocOOTask locooTask)
	{
		ParallelBroker parallelBroker = null;
		LocOOTaskResult result = null;

		parallelBroker = ParallelBroker.create(
				locooTask.getProperties().getProperty("parallelMode", "sequential"));

		parallelBroker.submit(locooTask);

		// wait for a result to be ready from the broker.
		result = (LocOOTaskResult) parallelBroker.getResultWait();
		parallelBroker.close();
		return result;
	}

	public LocOOTaskResult locateNonStatic(LocOOTask locooTask)
	{
		locooTask.run();
		return (LocOOTaskResult) locooTask.getResultObject();
	}

	//	/**
	//	 * Ensure that all the directories and files that are specified in the properties
	//	 * file actually exist.
	//	 * @throws PropertiesPlusException 
	//	 * @throws GMPException 
	//	 * @throws LocOOException 
	//	 * @throws IOException 
	//	 */
	//	private void testFilesInProperties(PropertiesPlusGMP properties) throws GMPException 
	//	{
	//		StringBuffer buf = new StringBuffer();
	//
	//		String predictors = properties.getProperty("loc_predictor_type", "");
	//		if (predictors.contains("tauptoolkit"))
	//		{
	//			buf.append(EllipticityCorrections.checkFiles(properties, "tauptoolkit"));
	//			buf.append(UncertaintyDistanceDependent.checkFiles(properties, "tauptoolkit"));
	//		}
	//
	//		if (predictors.contains("lookup2d"))
	//		{
	//			buf.append(EllipticityCorrections.checkFiles(properties, "lookup2d"));
	//			buf.append(UncertaintyDistanceDependent.checkFiles(properties, "lookup2d"));
	//		}
	//
	//		if (predictors.contains("bender"))
	//		{
	//			if (properties.getFile("benderModel") == null)
	//				buf.append(String.format("benderModel is not specified in property file%n"));
	//			else if (!properties.getFile("benderModel").exists())
	//				buf.append(String.format("benderModel = %s does not exist in file system%n", 
	//						properties.getProperty("benderModel")));
	//			buf.append(UncertaintyDistanceDependent.checkFiles(properties, "bender"));
	//		}
	//
	//		if (predictors.contains("slbm"))
	//		{
	//			if (properties.getFile("slbmModel") == null)
	//				buf.append(String.format("slbmModel is not specified in property file%n"));
	//			else if (!properties.getFile("slbmModel").exists())
	//				buf.append(String.format("slbmModel = %s does not exist in file system%n", 
	//						properties.getProperty("slbmModel")));
	//		}
	//
	//		if (buf.length() > 0)
	//			throw new LocOOException(String.format(buf.toString()));
	//	}

	/**
	 * Sets up status log and error log based on properties in property file:
	 * <ul>
	 * <li><b>io_verbosity</b> int
	 * <li><b>io_print_to_screen</b> boolean
	 * <li><b>io_log_file</b> String name of file to which status log will be output.
	 * Default is no output.
	 * <li><b>io_print_errors_to_screen</b> boolean defaults to true
	 * <li><b>io_error_file</b> String name of file to which error messages are written. 
	 * Defaults to "locoo_errors.txt"
	 * </ul>
	 * @param properties
	 */
	private void setupLoggers(PropertiesPlusGMP properties) 
	{

		try
		{
			errorlog = new ScreenWriterOutput();

			logger = new ScreenWriterOutput();
			logger.setVerbosity(properties.getInt("io_verbosity", 1));

			File logfile = null;
			File errFile = null;

			if (properties.getBoolean("io_print_to_screen", true))
				logger.setScreenOutputOn();
			if (properties.getProperty("io_log_file") != null)
			{
				logfile = new File(properties.getProperty("io_log_file"));
				logger.setWriter(new BufferedWriter(new FileWriter(logfile)));
				logger.setWriterOutputOn();
			}
			// turn logger off and back on to ensure current status is stored.
			logger.turnOff();
			logger.restore();

			if (!logger.isOutputOn())
				logger.setVerbosity(0);

			if (properties.getBoolean("io_print_errors_to_screen", logger.getVerbosity() > 0))
				errorlog.setScreenOutputOn();

			errFile = new File(properties.getProperty("io_error_file", "locoo_errors.txt"));
			errorlog.setWriter(new BufferedWriter(new FileWriter(errFile)));
			errorlog.setWriterOutputOn();
			// turn logger off and back on to ensure current status is stored.
			errorlog.turnOff();
			errorlog.restore();

			if (logger.getVerbosity() > 0)
			{
				logger.write(String.format("LocOO3D v. %s started %s%n%n",
						getVersion(), GMTFormat.localTime.format(new Date())));
				
				if (logger.getVerbosity() > 1)
				{
					logger.writeln("LocOO3D dependencies:");
					try {
						logger.writeln(Utils.getDependencyVersions());
					} catch (IOException e) {
						for (String d : getDependencies())
							logger.writeln(d);
						logger.writeln();
					}
				}
				if (logfile == null)
					logger.write(String.format("Status log file is off%n"));
				else
					logger.write(String.format("Status log file = %s%n", 
							logfile.getCanonicalPath()));
			}
		} 
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}

	}
	
	static public Collection<String> getDependencies()
	{
		Collection<String> dependencies = new TreeSet<>();
		addDependencies(dependencies);
		return dependencies;
	}
	
	static private void addDependencies(Collection<String> dependencies)
	{
		dependencies.add("LocOO3D "+getVersion());
		PredictorFactory.addDependencies(dependencies);
	}
	
}
