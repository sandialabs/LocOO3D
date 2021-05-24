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

package gov.sandia.gmp.locoo3d;

import gov.sandia.gmp.baseobjects.Location;
import gov.sandia.gmp.baseobjects.PropertiesPlusGMP;
import gov.sandia.gmp.baseobjects.Receiver;
import gov.sandia.gmp.baseobjects.Source;
import gov.sandia.gmp.baseobjects.globals.DBTableTypes;
import gov.sandia.gmp.baseobjects.globals.GeoAttributes;
import gov.sandia.gmp.baseobjects.globals.SeismicPhase;
import gov.sandia.gmp.baseobjects.interfaces.PredictorInterface;
import gov.sandia.gmp.baseobjects.interfaces.TopographyModelInterface;
import gov.sandia.gmp.geotessgmp.topomodel.TopoModelGeoTess;
import gov.sandia.gmp.predictorfactory.PredictorFactory;
import gov.sandia.gmp.util.exceptions.GMPException;
import gov.sandia.gmp.util.globals.Globals;
import gov.sandia.gmp.util.logmanager.ScreenWriterOutput;
import gov.sandia.gmp.util.propertiesplus.PropertiesPlusException;
import gov.sandia.gnem.dbtabledefs.nnsa_kb_core.Assoc;

import java.io.IOException;
import java.util.*;

/**
 * <p>
 * Title: LocOO
 * </p>
 * 
 * <p>
 * Description: Seismic Event Locator
 * </p>
 */
@SuppressWarnings("serial")
public class EventList extends HashMap<Long, Event>
{

	// if this is true, and correlated observations is on,
	// then voluminous output related to the correlation info
	// is output to the logger.
	protected static boolean debugCorrelatedObservations = false;

	protected PropertiesPlusGMP properties;

	protected ScreenWriterOutput logger, errorlog;
	
	//private predictors

	protected boolean eUseAllAttributes = true;

	private CorrelationMethod correlationMethod;
	private double correlationScale;
	protected HashMap<String, HashMap<String, Double>> correlations;

	private boolean allowBigResiduals;
	private double bigResidualThreshold;
	private double bigResidualMaxFraction;
	private String eInitialLocationMethod;

	/**
	 * The number of times an observation can change from defining 
	 * to non-defining before it is set non-defining permanently.
	 */
	private int nObservationFlipFlops;

	protected TopographyModelInterface topoModel;
	boolean gen_depth_init_topography;
	protected double gen_depth_init_value;

	boolean gen_min_depth_topography;
	protected double gen_min_depth_value;
	protected double gen_max_depth_value;

	protected boolean useTTPathCorrections;
	protected boolean useShPathCorrections;
	protected boolean useAzPathCorrections;

	protected boolean useTTModelUncertainty;
	protected boolean useAzModelUncertainty;
	protected boolean useShModelUncertainty;

	protected boolean allowCorePhaseRenamingP;
	protected double corePhaseRenamingThresholdDistanceP;

	protected Set<String> definingStations = null;
	protected EnumSet<SeismicPhase> definingPhases = 
			EnumSet.noneOf(SeismicPhase.class);
	protected EnumSet<GeoAttributes> definingAttributes = 
			EnumSet.noneOf(GeoAttributes.class);

	protected ObservationFilter observationFilter;

	protected Location parFileLocation;

	protected boolean[] fixed;

	public enum CorrelationMethod { UNCORRELATED, FILE, FUNCTION }

	protected boolean useSimplex;

	private boolean masterEventCorrectionsOn;
	private boolean masterEventUseOnlyStationsWithCorrections;

	/**
	 * 
	 * @param properties
	 * @param logger
	 * @param errorlog
	 * @param sources
	 * @param observations Map from orid (or sourceid) -> list of observations
	 * @param receivers Map from receiverId -> receiver
	 * @param predictors
	 * @throws Exception 
	 * @parameters masterEventCorrections
	 */
	public EventList(PropertiesPlusGMP properties,
			ScreenWriterOutput logger, ScreenWriterOutput errorlog, 
			Collection<Source> sources,
			HashMap<Long, ArrayList<LocOOObservation>> observations, 
			HashMap<Long, Receiver> receivers,
			PredictorFactory predictors, HashMap<String, double[]> masterEventCorrections)
					throws Exception
	{
		this.properties = properties;
		this.logger = logger;
		this.errorlog = errorlog;

		setup();

		// master event corrections;
		double[] meCorr = new double[] {Assoc.TIMERES_NA, Assoc.AZRES_NA, Assoc.SLORES_NA};
		
		HashMap<SeismicPhase, PredictorInterface> phasePredictorMap = new HashMap<>();

		for (Source source : sources)
		{
			ArrayList<LocOOObservation> obslist = observations.get(source.getSourceId());

			if (obslist == null || obslist.isEmpty())
				// this check added by sb 2013-10-01
				errorlog.writeln(String.format(
						"%nIgnoring event orid=%d evid=%d because it has no associated observations%n",
						source.getSourceId(), source.getEvid()));
			else
			{
				Event event = new Event(this, source);

				this.put(source.getSourceId(), event);

				for (LocOOObservation obs : obslist)
				{
					Receiver receiver = receivers.get(obs.receiverid);
					if (receiver == null)
						errorlog.writeln(String.format(
								"%nIgnoring observation that has no receiver associated with it. "
										+"orid %d, evid %d, arid %d, receiverId %d%n",
										source.getSourceId(), source.getEvid(),
										obs.observationid, obs.receiverid));
					else if (obs.phase == SeismicPhase.NULL)
						errorlog.writeln(String.format(
								"%nIgnoring observation with unrecognized phase %s. "
										+"orid %d, evid %d, arid %d, receiverId %d%n",
										obs.phaseName,
										source.getSourceId(), source.getEvid(),
										obs.observationid, obs.receiverid));
					else
					{
						if (masterEventCorrectionsOn)
						{
							// Units are tt (sec), az (radians), sh (sec/radian)
							meCorr = masterEventCorrections.get(receiver.getSta()+"/"+obs.phaseName);
							if (meCorr == null)
							{
								meCorr = new double[] {Assoc.TIMERES_NA, Assoc.AZRES_NA, Assoc.SLORES_NA};
								if (masterEventUseOnlyStationsWithCorrections)
								{
									obs.timedef = 'n';
									obs.azdef = 'n';
									obs.slodef = 'n';
								}
							}
						}

						PredictorInterface predictor = phasePredictorMap.get(obs.phase);
						if (predictor == null)
						{
							predictor = predictors.getPredictor(obs.phase);
							phasePredictorMap.put(obs.phase, predictor);
						}
						event.addArrival(new Arrival(receivers.get(obs.receiverid), event, obs, 
								predictor, meCorr));
					}
				}

				event.checkStationsAndPhases();
			}
		}
	}

	private void setup() throws GMPException, IOException
	{
		eInitialLocationMethod = properties.getProperty(
				"gen_initial_location_method", "data_file").toLowerCase();

		double lat,lon,depth=0.,etime;

		gen_depth_init_topography = properties.getProperty("gen_depth_init", "").toLowerCase().startsWith("topo");

		if (gen_depth_init_topography)
			gen_depth_init_value = Globals.NA_VALUE;
		else
			depth = gen_depth_init_value = properties.getDouble("gen_depth_init", Globals.NA_VALUE);


		parFileLocation = null;
		lat = properties.getDouble("gen_lat_init", Globals.NA_VALUE);
		if (lat >= -90.)
		{
			lon = properties.getDouble("gen_lon_init", Globals.NA_VALUE);
			if (lon > -360.)
			{
				if (depth != Globals.NA_VALUE)
				{
					if (properties.containsKey("gen_origin_time_init"))
						etime = properties.getDouble("gen_origin_time_init", Globals.NA_VALUE);
					else
						etime = Globals.NA_VALUE;

					if (etime > -999998)
					{
						parFileLocation = new Location(lat, lon, depth, true, etime);
					}
				}
			}
		}

		String gen_min_depth_string = properties.getProperty("gen_min_depth", "Globals.NA_VALUE");
		gen_min_depth_topography = gen_min_depth_string.toLowerCase().startsWith("topo");

		if (gen_min_depth_topography || gen_depth_init_topography)
		{
			//topoModel = new TopoModelGeoModel();
			try 
			{ 
	            topoModel = new TopoModelGeoTess(properties);
			} 
			catch (Exception e) 
			{ 
				throw new GMPException(e); 
			}
		}

		if (gen_depth_init_topography && !topoModel.isValid())
			throw new LocOOException(String.format(
					"gen_depth_init is topography but topo_model is not valid.%n"));

		if (gen_min_depth_topography && !topoModel.isValid())
			throw new LocOOException(String.format(
					"%ngen_min_depth is topography but topo_model is not valid.%n"));


		try 
		{
			gen_min_depth_value = Double.parseDouble(gen_min_depth_string);
		} 
		catch (NumberFormatException e) 
		{			
			gen_min_depth_value = Globals.NA_VALUE;
		}

		gen_max_depth_value = properties.getDouble("gen_max_depth", -Globals.NA_VALUE);

		if (eInitialLocationMethod.equalsIgnoreCase("properties_file")
				&& parFileLocation == null)
		{
			StringBuffer buf = new StringBuffer();
			buf.append(String.format("Unable to read initial location from properties file.%n"));
			buf.append(String.format("%s = %s%n",
					"gen_lat_init",	properties.getProperty("gen_lat_init", "-999.")));
			buf.append(String.format("%s = %s%n",
					"gen_lon_init",	properties.getProperty("gen_lon_init", "-999.")));
			buf.append(String.format("%s = %s%n",
					"gen_depth_init",	properties.getProperty("gen_depth_init", "-999.")));
			buf.append(String.format("%s = %s%n",
					"gen_origin_time_init",	properties.getProperty("gen_origin_time_init", "-999999.")));

			throw new LocOOException(buf.toString());
		}

		fixed = new boolean[] {
				properties.getBoolean("gen_fix_lat_lon", false),
				properties.getBoolean("gen_fix_lat_lon", false),
				properties.getBoolean("gen_fix_depth", false),
				properties.getBoolean("gen_fix_origin_time", false), };

		correlationMethod = CorrelationMethod.valueOf(properties.getProperty(
				"gen_correlation_matrix_method", "uncorrelated").toUpperCase());
		if (correlationMethod == CorrelationMethod.FUNCTION)
		{
			correlationScale = properties.getDouble("gen_correlation_scale", 10.);
			if (correlationScale < 1e-30)
				correlationMethod = CorrelationMethod.UNCORRELATED;
		}
		else
			correlationScale = Globals.NA_VALUE;

		if (correlationMethod == CorrelationMethod.FILE)
			readCorrelationData();

		if (properties.containsKey("debugCorrelatedObservations"))
			debugCorrelatedObservations = properties.getBoolean("debugCorrelatedObservations", false);

		allowBigResiduals = properties.getBoolean(
				"gen_allow_big_residuals", true);
		bigResidualThreshold = properties.getDouble(
				"gen_big_residual_threshold", 3.);
		bigResidualMaxFraction = properties.getDouble(
				"gen_big_residual_max_fraction", 0.2);

		nObservationFlipFlops = properties.getInt("nObservationFlipFlops", 10);

		observationFilter = new ObservationFilter(
				properties.getProperty("gen_defining_observations_filter"));

		// if definingStations is empty, it means accept all stations.
		// otherwise accept only stations in the Set.
		definingStations = new TreeSet<String>();
		String defining = properties.getProperty("gen_defining_stations");
		if (defining != null)
			for (String sta : defining.split(","))
				definingStations.add(sta.trim());

		defining = properties.getProperty("gen_defining_phases");
		if (defining == null)
			definingPhases = EnumSet.allOf(SeismicPhase.class);
		else
		{
			definingPhases = EnumSet.noneOf(SeismicPhase.class);
			for (String phase : defining.split(","))
			{
				SeismicPhase ph = SeismicPhase.valueOf(phase.trim());
				if (ph == null)
					errorlog.write(String.format(
							"WARNING: property gen_defining_phases = %s includes "
									+"unrecognized phase %s.  It is being ignored.",
									defining, phase));
				else
					definingPhases.add(ph);
			}
		}

		defining = properties.getProperty("gen_defining_attributes");
		if ((defining == null) || (defining.toLowerCase().trim().equals("all")))
			definingAttributes = EnumSet.of(
					GeoAttributes.TRAVEL_TIME, 
					GeoAttributes.AZIMUTH, 
					GeoAttributes.SLOWNESS);
		else
		{
			definingAttributes = EnumSet.noneOf(GeoAttributes.class);
			for (String attribute : defining.replaceAll(",", " ").split("\\s+"))
				if (attribute.toLowerCase().trim().startsWith("t"))
					definingAttributes.add(GeoAttributes.TRAVEL_TIME);
				else if (attribute.toLowerCase().trim().startsWith("a"))
					definingAttributes.add(GeoAttributes.AZIMUTH);
				else if (attribute.toLowerCase().trim().startsWith("s"))
					definingAttributes.add(GeoAttributes.SLOWNESS);
				else
					errorlog.write(String.format(
							"WARNING: property gen_defining_attributes = %s includes "
									+"unrecognized attribute %s.  It is being ignored.  "
									+"Attributes must start with 't', 'a', or 's'",
									defining, attribute));

			if (definingAttributes.isEmpty())
				throw new LocOOException(
						"Property gen_defining_attributes = " + defining +
						" resulted in 0 defining attributes."
						);
		}

		useTTPathCorrections = properties.getBoolean("use_tt_path_corrections", true);
		useShPathCorrections = properties.getBoolean("use_sh_path_corrections", true);
		useAzPathCorrections = properties.getBoolean("use_az_path_corrections", true);

		useTTModelUncertainty = properties.getBoolean("use_tt_model_uncertainty", true);
		useAzModelUncertainty = properties.getBoolean("use_az_model_uncertainty", true);
		useShModelUncertainty = properties.getBoolean("use_sh_model_uncertainty", true);

		allowCorePhaseRenamingP = properties.getBoolean("allowCorePhaseRenamingP", false);
		if (allowCorePhaseRenamingP)
			corePhaseRenamingThresholdDistanceP = 
			properties.getDouble("corePhaseRenamingThresholdDistanceP", 110.);

		useSimplex = properties.getBoolean("useSimplex", false);

		masterEventCorrectionsOn = properties.containsKey("masterEventWhereClause");
		masterEventUseOnlyStationsWithCorrections = properties.getBoolean("masterEventUseOnlyStationsWithCorrections", false);
	}

	private void readCorrelationData() 
			throws LocOOException, PropertiesPlusException, IOException
	{
		if (!properties.containsKey("gen_correlation_matrix_file"))
			throw new LocOOException("gen_correlation_matrix_method = file but gen_correlation_matrix_file is not specified in properties file.");

		String staPhaseType1, staPhaseType2;
		Double correlation;
		HashMap<String, Double> values;

		correlations =  new HashMap<String, HashMap<String,Double>>();

		Scanner input = new Scanner(properties.getFile("gen_correlation_matrix_file"));
		while (input.hasNext())
		{
			staPhaseType1 = input.next();
			staPhaseType2 = input.next();
			correlation = input.nextDouble();

			// ensure that last two characters are upper case
			staPhaseType1 = staPhaseType1.substring(0, staPhaseType1.length()-2)+
					staPhaseType1.substring(staPhaseType1.length()-2).toUpperCase();
			staPhaseType2 = staPhaseType2.substring(0, staPhaseType2.length()-2)+
					staPhaseType2.substring(staPhaseType2.length()-2).toUpperCase();

			if (Math.abs(correlation) > 1)
				throw new LocOOException(String.format(
						"%nError reading correlation data from file "+
								properties.getFile("gen_correlation_matrix_file").getCanonicalPath()+
								"%nAttempting to set %s -> %s = %1.6f%n"+
								"but correlation coefficients must be between -1 and 1.%n",
								staPhaseType1, staPhaseType2, correlation));

			values = correlations.get(staPhaseType1);
			if (values == null)
			{
				values = new HashMap<String, Double>();
				correlations.put(staPhaseType1, values);
			}
			values.put(staPhaseType2, correlation);

			values = correlations.get(staPhaseType2);
			if (values != null && values.get(staPhaseType1) != null
					&& !values.get(staPhaseType1).equals(correlation))
				throw new LocOOException(String.format(
						"%nError reading correlation data from file "+
								properties.getFile("gen_correlation_matrix_file").getCanonicalPath()+
								"%nAttempting to set %s -> %s = %1.6f and %s -> %s = %1.6f%n"+
								"but matrix of correlation coefficients must be symmetric.%n",
								staPhaseType1, staPhaseType2, correlation,
								staPhaseType2, staPhaseType1, values.get(staPhaseType1)));

		}
		input.close();
	}

	/**
	 * getCorrelationMethod
	 * 
	 * @return Object
	 */
	public CorrelationMethod getCorrelationMethod()
	{
		return correlationMethod;
	}

	/**
	 * getCorrelationScale
	 * 
	 * @return double
	 */
	public double getCorrelationScale()
	{
		return correlationScale;
	}

	/**
	 * if false, then observations with large weighted residuals
	 * will be set to non-defining.
	 * 
	 * @return boolean
	 */
	public boolean getAllowBigResiduals()
	{
		return allowBigResiduals;
	}

	/**
	 * if getAllowBigResiduals() is false, then observations with
	 * weighted residuals (absolute value) larger than this value will
	 * be set to non-defining. 
	 * 
	 * @return double
	 */
	public double getBigResidualThreshold()
	{
		return bigResidualThreshold;
	}

	/**
	 * if there are observations with large residuals that are to be
	 * set to non-defining, this number will control how many of those
	 * observations can be set to non-defining.  The maximum number is 
	 * (N-M) * bigResidualMaxFraction.  The minimum number is 1.
	 * 
	 * @return double
	 */
	public double getBigResidualMaxFraction()
	{
		return bigResidualMaxFraction;
	}

	/**
	 * 
	 * @return String
	 */
	public String getInitialLocationMethod()
	{
		return eInitialLocationMethod;
	}

	/**
	 * 
	 * @return Location
	 */
	public Location getParFileLocation()
	{
		return parFileLocation;
	}

	public void setCorrelationMethod(CorrelationMethod correlationMethod)
	{
		this.correlationMethod = correlationMethod;
	}

	public void setCorrelationScale(double correlationScale)
	{
		this.correlationScale = correlationScale;
	}

	public void setAllowBigResiduals(boolean allowBigResiduals)
	{
		this.allowBigResiduals = allowBigResiduals;
	}

	public void setBigResidualThreshold(double bigResidualThreshold)
	{
		this.bigResidualThreshold = bigResidualThreshold;
	}

	public void setBigResidualMaxFraction(double bigResidualMaxFraction)
	{
		this.bigResidualMaxFraction = bigResidualMaxFraction;
	}

	/**
	 * getFixed
	 * 
	 * @return boolean[]
	 */
	public boolean[] getFixed()
	{
		return fixed;
	}

	/**
	 * After calling locateEvents() call this method to extract the results in the 
	 * form of a LocooTaskResult object.
	 * @param results
	 * @throws GMPException 
	 * @throws IOException 
	 */
	public void setResults(LocOOTaskResult results) 
			throws GMPException, IOException
	{
		//Set<DBTableTypes> tables = new HashSet<DBTableTypes>();
		EnumSet<DBTableTypes> tables = EnumSet.noneOf(DBTableTypes.class);

		String outputTableTypes = properties.getProperty("outputTableTypes");
		if (outputTableTypes == null)
			outputTableTypes = properties.getProperty("dbOutputTableTypes");

		if (outputTableTypes == null)
			throw new LocOOException(String.format(
					"%nProperty dbOutputTableTypes is not specified in the properties file. "
							+"No output will be generated.%n"));

		for (String tableType : outputTableTypes.split(","))
		{
			tableType = tableType.trim();
			if (tableType.length() > 0)
			{
				DBTableTypes type = DBTableTypes.valueOf(tableType.toUpperCase());
				if (type == null)
					errorlog.writeln(tableType
							+" is specified in property file with parameter "
							+"dbOutputTableTypes but is not an element of enum DBTableTypes");
				else
					tables.add(type);
			}
		}

		int index=0;
		for (Event event : this.values())
		{
			LocOOResult result = new LocOOResult(index++, event, tables, results.getOriginalArrivals());
			if (!result.isValid())
				results.getErrorlog().append(String.format(
						"Error occurred processing orid %d evid %d: %s%n", 
						event.getSourceId(), event.getEvid(), result.getErrorMessage()));
			results.addResult(result);
		}
	}

	public PropertiesPlusGMP getProperties()
	{
		return properties;
	}

	/**
	 * @return the useSiteTerms
	 */
	public boolean useTTPathCorrections()
	{
		return useTTPathCorrections;
	}

	/**
	 * @param usePathCorrections the useSiteTerms to set
	 */
	public void setUseTTPathCorrections(boolean usePathCorrections)
	{
		this.useTTPathCorrections = usePathCorrections;
	}

	public boolean useShPathCorrections()
	{
		return useShPathCorrections;
	}

	public void setUseShPathCorrections(boolean useShPathCorrections)
	{
		this.useShPathCorrections = useShPathCorrections;
	}

	public boolean useAzPathCorrections()
	{
		return useAzPathCorrections;
	}

	public void setUseAzPathCorrections(boolean useAzPathCorrections)
	{
		this.useAzPathCorrections = useAzPathCorrections;
	}

	/**
	 * The number of times an observation can change from defining 
	 * to non-defining before it is set non-defining permanently.
	 * @return
	 */
	public int getnObservationFlipFlops() {
		return nObservationFlipFlops;
	}

}
