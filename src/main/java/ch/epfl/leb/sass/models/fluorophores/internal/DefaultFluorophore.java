/* 
 * Copyright (C) 2017 Laboratory of Experimental Biophysics
 * Ecole Polytechnique Federale de Lausanne
 * 
 * Author: Marcel Stefko
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
package ch.epfl.leb.sass.models.fluorophores.internal;

import ch.epfl.leb.sass.models.emitters.internal.AbstractEmitter;
import ch.epfl.leb.sass.utils.RNG;
import ch.epfl.leb.sass.models.legacy.Camera;
import ch.epfl.leb.sass.models.psfs.PSFBuilder;
import ch.epfl.leb.sass.models.fluorophores.Fluorophore;
import ch.epfl.leb.sass.models.Model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Random;

/**
 * A general fluorescent molecule which emits light.
 * @author Marcel Stefko
 */
public class DefaultFluorophore extends AbstractEmitter implements Fluorophore, Model {
    
    /**
     * internal emitter clock for tracking total time elapsed
     */
    private double time_elapsed = 0.0;
    
    /**
     * RNG
     */
    private final Random random;
    
    /**
     * Internal state system for this fluorophore
     */
    private final StateSystem state_system;
    
    /**
     * State that this fluorophore is currently in
     */
    private int current_state;
    
    /**
     * No of photons per frame.
     */
    private final double signal;
    
    /**
     * Initialize fluorophore and calculate its pattern on camera
     * @param camera Camera used for calculating diffraction pattern
     * @param signal No of photons per frame.
     * @param state_system Internal state system for this fluorophore
     * @param start_state Initial state number
     * @param x x-position in pixels
     * @param y y-position in pixels
     * @deprecated 
     */
    @Deprecated
    public DefaultFluorophore(Camera camera, double signal, StateSystem state_system, int start_state, double x, double y) {
        super(camera, x, y);
        this.state_system = state_system;
        this.signal = signal;
        this.current_state = start_state;
        if (start_state >= state_system.getNStates()) {
            throw new IllegalArgumentException("Starting state no. is out of bounds.");
        }
        this.random = RNG.getUniformGenerator();
        
        // Log the fluorophore's position
        this.positionLogger.logPosition(this.getId(), x, y, 0.0);
    }
    
     /**
     * Initialize fluorophore and calculate its pattern on camera
     * @param psfBuilder The Builder for calculating microscope PSFs.
     * @param signal Number of photons per frame.
     * @param state_system Internal state system for this fluorophore
     * @param start_state Initial state number
     * @param x x-position in pixels
     * @param y y-position in pixels
     * @param z z-position in pixels
     */
    public DefaultFluorophore(
            PSFBuilder psfBuilder,
            double signal,
            StateSystem state_system,
            int start_state,
            double x,
            double y,
            double z) {
        super(x, y, z, psfBuilder);
        this.state_system = state_system;
        this.signal = signal;
        this.current_state = start_state;
        if (start_state >= state_system.getNStates()) {
            throw new IllegalArgumentException("Starting state no. is out of bounds.");
        }
        this.random = RNG.getUniformGenerator();
        
        // Log the fluorophore's position
        this.positionLogger.logPosition(this.getId(), x, y, z);
    }

    /**
     * Sample an random number from an exponential distribution
     * @param mean mean of the distribution
     * @return random number from this distribution
     */
    protected final double nextExponential(double mean) {
        if (java.lang.Double.isInfinite(mean))
            return java.lang.Double.POSITIVE_INFINITY;
        else
            return Math.log(1 - random.nextDouble()) * (-mean);
    }

    /**
     * Returns the current state of the emitter (on or off), but does not
     * inform if this emitter is also bleached!
     * @return true-emitter is on, false-emitter is off
     */
    public boolean isOn() {
        return state_system.isOnState(current_state);
    }

    /**
     * Informs if this emitter switched into the irreversible bleached state.
     * @return boolean, true if emitter is bleached
     */
    public boolean isBleached() {
        return state_system.isBleachedState(current_state);
    }
    
    /**
     * Recalculates the lifetimes of this emitter based on current laser power.
     * @param laserPower current laser power
     */
    @Override
    public void recalculateLifetimes(double laserPower) {
        this.state_system.recalculate_lifetimes(laserPower);
    }

    @Override
    protected double simulateBrightness() {
        if (isBleached()) {
            return 0.0;
        }
        
        double remaining_time = 1.0;
        double on_time = 0.0;
        while (remaining_time > 0.0) {
            // initialize time of next transition and next state id variables
            double transition_time = java.lang.Double.POSITIVE_INFINITY; 
            int next_state = current_state;
            // for each state transition, draw lifetime of this transition,
            // and keep track which one is the minimal one
            for (int state=0; state<state_system.getNStates(); state++) {
                double state_time = nextExponential(state_system.getMeanTransitionLifetime(current_state, state));
                if (state_time < transition_time) {
                    next_state = state;
                    transition_time = state_time;
                }
            }
            // transition happens sooner than end of frame
            if (transition_time <= remaining_time) {
                if (this.isOn()) {
                    on_time += transition_time;
                }
                remaining_time -= transition_time;
                time_elapsed += transition_time;
                
                stateLogger.logStateTransition(
                    this.getId(),
                    time_elapsed,
                    current_state,
                    next_state
                );
                
                current_state = next_state;
            // no transition happens till end of frame
            } else {
                if (this.isOn()) {
                    on_time += remaining_time;
                }
                time_elapsed += remaining_time;
                remaining_time = 0.0;
            }
        }
        // The brightness of the fluorophore
        double brightness = flicker(on_time*signal);
        
        // If the fluorophore was on during that frame, write a line in the frame logger
        if (on_time > 0.0) {
            // Round time_elapsed to the lower integer, to get the current frame
            // If on_time = 1.0, then frame = int(time_elapsed), hence 0.9999 rather than 1
            int frame = (int) (time_elapsed + 0.999999);
            frameLogger.logFrame(frame, this.getId(), this.x, this.y, this.z, brightness, on_time);
        }
        return brightness;
    }
    
    /**
     * Returns the id of the fluorophore state system's current state.
     * 
     * @return The id of the current state of the fluorophore's state system.
     */
    public int getCurrentState() {
        return current_state;
    }
    
    /**
     * Returns the fluorophore's number of photons per frame.
     * 
     * @return The number of photons per frame emitted by the fluorophore.
     */
    public double getSignal() {
        return signal;
    }
    
    /**
     * Returns the fluorophore's properties as a JSON string.
     * @return The properties of the fluorophore as a JSON string.
     */
    @Override
    public JsonElement toJson() {
        Gson gson = new GsonBuilder()
                        .registerTypeAdapter(DefaultFluorophore.class,
                                             new DefaultFluorophoreSerializer())
                        .create();
        return gson.toJsonTree(this);
    }
    
    /**
     * Return the x-position of the fluorophore.
     * 
     * @return The fluorophore's x-position.
     */
    public double getX() { return this.x; }
    
    /**
     * Return the y-position of the fluorophore.
     * 
     * @return The fluorophore's y-position.
     */
    public double getY() { return this.y; }
    
    /**
     * Return the z-position of the fluorophore.
     * 
     * @return The fluorophore's z-position.
     */
    public double getZ() { return this.z; }
}

class DefaultFluorophoreSerializer implements JsonSerializer<DefaultFluorophore> {
    @Override
    public JsonElement serialize(DefaultFluorophore src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.add("id", new JsonPrimitive(src.getId()));
        result.add("x", new JsonPrimitive(src.x));
        result.add("y", new JsonPrimitive(src.y));
        result.add("z", new JsonPrimitive(src.z));
        result.add("currentState", new JsonPrimitive(src.getCurrentState()));
        result.add("photonsPerFrame", new JsonPrimitive(src.getSignal()));
        result.add("bleached", new JsonPrimitive(src.isBleached()));
        result.add("emitting", new JsonPrimitive(src.isOn()));
        return result;
    }
}

