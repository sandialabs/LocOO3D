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

public class AzGap
{

	/**
	 * Primary azimuthal gap, in radians
	 */
	private double azgap1 = -1;

	/**
	 * Secondary azimuthal gap, in radians
	 */
	private double azgap2 = -1;

	/**
	 * Station left out for evaluation of secondary azgap.
	 */
	private String sta = "-";

	/**
	 * total number of stations
	 */
	private long nsta = -1;

	/**
	 * number of stations within 30 km of event
	 */
	private long nsta30 = -1;

	/**
	 * Number of stations within 250 km of event
	 */
	private long nsta250 = -1;

	public AzGap()
	{
	}

	/**
	 * 
	 * @param azgap1 Primary azimuthal gap, in radians
	 * @param azgap2 Secondary azimuthal gap, in radians
	 * @param sta Station left out for evaluation of secondary azgap.
	 * @param nsta total number of stations
	 * @param nsta30 number of stations within 30 km of event
	 * @param nsta250 Number of stations within 250 km of event
	 */
	public AzGap(double azgap1, double azgap2, String sta,
			long nsta, long nsta30, long nsta250)
	{
		this.azgap1 = azgap1;
		this.azgap2 = azgap2;
		this.sta = sta;
		this.nsta = nsta;
		this.nsta30 = nsta30;
		this.nsta250 = nsta250;
	}

	/**
	 * Primary azimuthal gap, in radians
	 * @return
	 */
	public double getAzgap1()
	{
		return azgap1;
	}

	/**
	 * Primary azimuthal gap, in degrees
	 * @return
	 */
	public double getAzgap1Degrees()
	{
		return Math.toDegrees(azgap1);
	}

	/**
	 * Primary azimuthal gap, in radians
	 * @param azgap1
	 * @return
	 */
	public AzGap setAzgap1(double azgap1)
	{
		this.azgap1 = azgap1;
		return this;
	}

	/**
	 * Secondary azimuthal gap, in radians
	 * @return
	 */
	public double getAzgap2()
	{
		return azgap2;
	}

	/**
	 * Primary azimuthal gap, in degrees
	 * @return
	 */
	public double getAzgap2Degrees()
	{
		return Math.toDegrees(azgap2);
	}

	/**
	 * Secondary azimuthal gap, in radians
	 * @param azgap2
	 * @return
	 */
	public AzGap setAzgap2(double azgap2)
	{
		this.azgap2 = azgap2;
		return this;
	}

	/**
	 * Station left out for evaluation of secondary azgap.
	 * @return
	 */
	public String getSta()
	{
		return sta;
	}

	/**
	 * Station left out for evaluation of secondary azgap.
	 * @param sta
	 * @return
	 */
	public AzGap setSta(String sta)
	{
		this.sta = sta;
		return this;
	}

	public long getNsta()
	{
		return nsta;
	}

	public AzGap setNsta(long nsta)
	{
		this.nsta = nsta;
		return this;
	}

	public long getNsta30()
	{
		return nsta30;
	}

	public AzGap setNsta30(long nsta30)
	{
		this.nsta30 = nsta30;
		return this;
	}

	public long getNsta250()
	{
		return nsta250;
	}

	public AzGap setNsta250(long nsta250)
	{
		this.nsta250 = nsta250;
		return this;
	}

}
