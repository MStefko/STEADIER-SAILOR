/*
 * Copyright (C) 2017 Laboratory of Experimental Biophysics
 * Ecole Polytechnique Federale de Lausanne
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ch.epfl.leb.sass.models.components.internal;

import ch.epfl.leb.sass.models.components.Objective;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

/**
 * Properties related to the microscope objective.
 * 
 * @author Kyle M. Douglass
 */
public final class DefaultObjective  implements Objective {
    /**
     * Numerical aperture
     */
    private final double NA;

    /**
     * Magnification of the objective.
     */
    private final double mag;
    
    public static class Builder {
       private double NA;
       private double mag;
       
       public Builder NA(double NA) {this.NA = NA; return this;}
       public Builder mag(double mag) {this.mag = mag; return this;}
       
       public DefaultObjective build() {
           return new DefaultObjective(this);
       }
    }
    
    /**
     * Creates a new microscope objective.
     * @param builder An Objective builder.
     */
    private DefaultObjective(Builder builder) {
        this.NA = builder.NA;
        this.mag = builder.mag;
    }
    
    /**
     * Computes the full width at half maximum of the Airy disk.
     * 
     * Units are the same as those of wavelength.
     * 
     * @param wavelength
     * @return Full width at half maximum size of the Airy disk.
     */
    @Override
    public double airyFWHM(double wavelength) {
        double airyDiskFWHM = 0.514*wavelength/this.NA * this.mag;

        return airyDiskFWHM;
    }
    
    /**
     * Computes the radius of the Airy disk.
     * 
     * Units are the same as those of wavelength.
     * 
     * @param wavelength
     * @return Distance from center of Airy disk to first minimum.
     */
    @Override
    public double airyRadius(double wavelength) {
        double airyDiskRadius = 0.61*wavelength/this.NA * this.mag;

        return airyDiskRadius;
    }
    
    /**
     * @return The objective's magnification.
     */
    @Override
    public double getMag() {
        return this.mag;
    }
    
    /**
     * @return The objective's numerical aperture
     */
    @Override
    public double getNA() {
        return this.NA;
    }
    
    /**
     * Outputs the laser's properties as a JSON element.
     * 
     * @return A JSON tree describing the laser's properties.
     */
    @Override
    public JsonElement toJson() {
        Gson gson = new GsonBuilder()
                        .registerTypeAdapter(DefaultObjective.class,
                                             new DefaultObjectiveSerializer())
                        .create();
        return gson.toJsonTree(this);
    }
    
    class DefaultObjectiveSerializer 
    implements JsonSerializer<DefaultObjective> {
        @Override
        public JsonElement serialize(DefaultObjective src, Type typeOfSrc,
                                  JsonSerializationContext context) {
            JsonObject result = new JsonObject();
            result.add("magnification", new JsonPrimitive(src.getMag()));
            result.add("numerical aperture", new JsonPrimitive(src.getNA()));
            return result;
        }
    }
}
