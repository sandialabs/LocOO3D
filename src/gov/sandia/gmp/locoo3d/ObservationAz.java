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

import gov.sandia.gmp.baseobjects.globals.GeoAttributes;
import gov.sandia.gmp.baseobjects.interfaces.PredictorInterface;
import gov.sandia.gmp.util.globals.Globals;

import java.util.EnumSet;

import static java.lang.Math.PI;

/**
 * <p>
 * Title: LocOO
 * </p>
 * 
 * <p>
 * Description: Seismic Event Locator
 * </p>
 */
public class ObservationAz extends ObservationComponent
{
	/**
	 * True if there is a valid predictor for this observation
	 * and that predictor is capable of computing predicted value,
	 * model uncertainty, and derivatives for this observation
	 * component.  Set in the constructors.
	 */
	final protected boolean supported;

	/**
	 * ObservationTT
	 * 
	 * @param arrival
	 *            Arrival
	 */
	public ObservationAz(Arrival arrival)
	{
		super(arrival);
		
		masterEventCorrection = arrival.masterEventCorrections[1];

		//		supported = arrival.getPredictor() != null
		//		   && arrival.getPredictor().getSupportedGeoAttributes().contains(getObsType())
		//		   && (!useModelUncertainty()
		//				   || arrival.getPredictor().getSupportedGeoAttributes().contains(getModelUncertaintyType()))
		//		   && arrival.getPredictor().getSupportedGeoAttributes().contains(DERIVS[0])
		//		   && arrival.getPredictor().getSupportedGeoAttributes().contains(DERIVS[1])
		//		   && arrival.getPredictor().getSupportedGeoAttributes().contains(DERIVS[2])
		//		   ;

		PredictorInterface p = arrival.getPredictor();
		supported = p != null 
				&& p.isSupported(getReceiver(), getPhase(), getObsType(), Globals.NA_VALUE)
				&& (!useModelUncertainty()
						|| p.isSupported(getReceiver(), getPhase(), getModelUncertaintyType(), Globals.NA_VALUE))
						&& p.isSupported(getReceiver(), getPhase(), DERIVS[0], Globals.NA_VALUE)
						&& p.isSupported(getReceiver(), getPhase(), DERIVS[1], Globals.NA_VALUE)
						&& p.isSupported(getReceiver(), getPhase(), DERIVS[2], Globals.NA_VALUE);

	}

	/**
	 * Returns true if the predictor is capable of computing a predicted value
	 * model uncertainty and derivatives.  The boolean value that backs this
	 * call was set in the ObservationComponent constructor and is final.
	 * 
	 * @return boolean
	 */
	@Override
	public boolean isSupported()
	{
		return supported;
	}

	@Override
	public GeoAttributes getObsType()
	{
		return GeoAttributes.AZIMUTH;
	}

	@Override
	public GeoAttributes getObsUncertaintyType()
	{
		return GeoAttributes.AZIMUTH_OBSERVED_UNCERTAINTY;
	}

	@Override
	public GeoAttributes getModelUncertaintyType()
	{
		return GeoAttributes.AZIMUTH_MODEL_UNCERTAINTY;
	}

	@Override
	public boolean useModelUncertainty()
	{
		return arrival.useAzModelUncertainty;
	}

	@Override
	public double getObserved()
	{
		return arrival.getAzimuth();
	}

	@Override
	public double getResidual()
	{
		if (isPredictionValid())
		{
			double residual = arrival.getAzimuth()
					-arrival.getPrediction().getAttribute(GeoAttributes.AZIMUTH);
			while (residual > PI)
				residual -= 2*PI;
			while (residual <= -PI)
				residual += 2*PI;
			return residual;
		}
		else
			return Globals.NA_VALUE;
	}

	@Override
	public double getObsUncertainty()
	{
		return arrival.getDelaz();
	}

	@Override
	public boolean isDefining()
	{
		return arrival.isAzdef();
	}

	@Override
	public void setDefining(boolean defining)
	{
		arrival.setAzdef(defining);
	}

	@Override
	public boolean isDefiningOriginal()
	{
		return arrival.isAzdefOriginal();
	}

	private static final GeoAttributes[] DERIVS = new GeoAttributes[] {
		GeoAttributes.DAZ_DLAT, GeoAttributes.DAZ_DLON,
		GeoAttributes.DAZ_DR, GeoAttributes.DAZ_DTIME };

	@Override
	protected GeoAttributes[] getDerivAttributes()
	{
		return DERIVS;
	}

	@Override
	protected GeoAttributes DObs_DLAT()
	{
		return GeoAttributes.DAZ_DLAT;
	}

	@Override
	protected GeoAttributes DObs_DLON()
	{
		return GeoAttributes.DAZ_DLON;
	}

	@Override
	protected GeoAttributes DObs_DR()
	{
		return GeoAttributes.DAZ_DR;
	}

	@Override
	protected GeoAttributes DObs_DTIME()
	{
		return GeoAttributes.DAZ_DTIME;
	}

	@Override
	protected double toOutput(double value)
	{
		if (value == Globals.NA_VALUE)
			return value;
		return Math.toDegrees(value);
	}

	@Override
	public String getObsTypeShort()
	{
		return "AZ";
	}	

	@Override
	public double getElevationCorrection()
	{
		return Globals.NA_VALUE;
	}

	@Override
	public double getElevationCorrectionAtSource()
	{
		return Globals.NA_VALUE;
	}

	@Override
	public double getEllipticityCorrection()
	{
		return Globals.NA_VALUE;
	}

	@Override
	public double getPathCorrection()
	{
		if (isPredictionValid())
			return arrival.getPrediction().getAttribute(
					GeoAttributes.AZIMUTH_PATH_CORRECTION);
		return Globals.NA_VALUE;
	}

	@Override
	public double getSiteCorrection()
	{
		if (isPredictionValid())
			return arrival.getPrediction().getAttribute(GeoAttributes.NA_VALUE);
		return Globals.NA_VALUE;
	}

	@Override
	public double getSourceCorrection()
	{
		if (isPredictionValid())
			return arrival.getPrediction().getAttribute(GeoAttributes.NA_VALUE);
		return Globals.NA_VALUE;
	}

	@Override
	public char getDefiningChar()
	{
		return arrival.getAzdefChar();
	}

	@Override
	protected void addRequiredAttributes(EnumSet<GeoAttributes> attributes, boolean needDerivatives)
	{
		super.addRequiredAttributes(attributes, needDerivatives);
		if (arrival.getEvent().getEvents().useAzPathCorrections())
			attributes.add(GeoAttributes.AZIMUTH_PATH_CORRECTION);
	}

	@Override
	public boolean usePathCorr()
	{
		return arrival.getEvent().getEvents().useAzPathCorrections;
	}

	@Override
	public GeoAttributes getPathCorrType()
	{
		return GeoAttributes.AZIMUTH_PATH_CORRECTION;
	}

	@Override
	public GeoAttributes getBaseModelType()
	{
		return GeoAttributes.AZIMUTH_BASEMODEL;
	}
}
