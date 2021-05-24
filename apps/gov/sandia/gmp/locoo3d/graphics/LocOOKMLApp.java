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

package gov.sandia.gmp.locoo3d.graphics;

import gov.sandia.gmp.locoo3d.LocOOKML;
import gov.sandia.gmp.util.numerical.vector.EarthShape;
import gov.sandia.gmp.util.propertiesplus.PropertiesPlus;
import gov.sandia.gmp.util.propertiesplus.PropertiesPlusException;
import gov.sandia.gnem.dbtabledefs.nnsa_kb_core_extended.OriginExtended;
import gov.sandia.gnem.dbtabledefs.nnsa_kb_core_extended.Schema;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class LocOOKMLApp {

	public static void main(String[] args) 
	{
		PropertiesPlus p = new PropertiesPlus();
		
		try 
		{
			p.setProperty("dbInputTablePrefix = nknukes_");
			p.setProperty("dbInputTableTypes = origin,origerr");
			Schema schema = new Schema("dbInput", p, false);
			
			File outputDir = new File(".\\LocOO3D\\special_event\\test_2016_09_08");
			
			HashSet<OriginExtended> origins = OriginExtended.readOriginExtended(schema, null, "1=1", null, null);
			
			for (OriginExtended o : origins)
				LocOOKML.run(o.getUnitVector(), 
						new double[] {o.getOrigerr().getSmajax(), o.getOrigerr().getSminax(), o.getOrigerr().getStrike()},
						String.format("orid_%d", o.getOrid()), new File(outputDir, String.format("orid_%d.kml", o.getOrid())));
		} 
		catch (Exception e1) 
		{
			e1.printStackTrace();
		}
		
		ArrayList<String> titles = new ArrayList<String>();
//		titles.add("2006-ak135");
//		titles.add("2009-ak135");  
//		titles.add("2016-ak135");  
//		titles.add("2006-salsa3d");
//		titles.add("2009-salsa3d");
//		titles.add("2016-salsa3d");
		
		titles.add("2016-JAN-06");  
		titles.add("2016-SEP-09");  
		titles.add("2016-SEP-09 relative");  
		
		ArrayList<double[]> locations = new ArrayList<double[]>();
//		locations.add(es.getVectorDegrees(41.312621, 129.023634));
//		locations.add(es.getVectorDegrees(41.305296, 129.079841));
//		locations.add(es.getVectorDegrees(41.334498,  129.07444));
//		locations.add(es.getVectorDegrees(41.323589, 129.183763));
//		locations.add(es.getVectorDegrees(41.329161, 129.130986));
//		locations.add(es.getVectorDegrees(41.306505, 129.094547));

		locations.add(EarthShape.WGS84.getVectorDegrees(41.315977, 129.090298));
		locations.add(EarthShape.WGS84.getVectorDegrees(41.326947, 129.071134));
		locations.add(EarthShape.WGS84.getVectorDegrees(41.302065, 129.126776));

		ArrayList<double[]> ellipses = new ArrayList<double[]>();
//		ellipses.add(new double[] {23.518052, 15.501638, 67.229555});
//		ellipses.add(new double[] {15.399293, 12.371816, 146.88633});
//		ellipses.add(new double[] {15.176066, 10.772064,  122.7684});
//		ellipses.add(new double[] {12.209887, 8.5333497,  74.77812});
//		ellipses.add(new double[] { 8.731046, 6.4742199, 142.48298});
//		ellipses.add(new double[] {9.2523294, 6.8761099, 136.29602});

		ellipses.add(new double[] {6.5777231,  4.6585492,  47.829405});
		ellipses.add(new double[] {8.5641415,	7.3093957,	105.31814});
		ellipses.add(new double[] {2.5961405, 2.1436225, 112.58921});
		
		try 
		{
			new LocOOKMLApp().run(locations, ellipses, titles, new File(
					".\\LocOO3D\\special_event\\test_2016_09_08"));
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		}

	}

	public void run(List<double[]> locations, List<double[]> ellipses, List<String>titles, File outputDir) throws Exception
	{
		outputDir.mkdirs();
		for (int i=0; i<locations.size(); ++i)
			LocOOKML.run(locations.get(i), ellipses.get(i), titles.get(i), new File(outputDir, titles.get(i)+".kml"));
	}

}
