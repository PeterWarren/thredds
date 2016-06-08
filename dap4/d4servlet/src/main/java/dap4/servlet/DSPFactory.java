/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.servlet;

import dap4.core.data.DSP;
import dap4.core.util.DapContext;
import dap4.core.util.DapException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Provide a factory for DSP instances
 */

abstract public class DSPFactory
{
    static public final String MATCHMETHOD = "dspMatch";

    //////////////////////////////////////////////////
    // Type decls

    static protected class Registration
    {
        public Class dspclass;
        public Method matcher;

        public Registration(Class dspclass)
        {
            this.dspclass = dspclass;
            try {
                this.matcher = dspclass.getMethod(MATCHMETHOD, String.class, DapContext.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("DSPFactory: no " + MATCHMETHOD + " method for DSP: " + dspclass.getName());
            }
        }
    }

    //////////////////////////////////////////////////
    // Instance variables

    /**
     * Define a map of known DSP classes.
     */
    protected List<Registration> dspRegistry = new ArrayList<>();

    //////////////////////////////////////////////////
    // Constructor(s)

    public DSPFactory()
    {
        // Subclasses should Register known DSP classes: order is important
        // in event that two or more dsps can match a given file
        // (e.q. FileDSP vs NetcdfDSP).
        // Only used in server
    }

    //////////////////////////////////////////////////
    // Methods

    /**
     * Register a DSP, using its class string name.
     *
     * @param className Class that implements DSP.
     * @throws IllegalAccessException if class is not accessible.
     * @throws InstantiationException if class doesnt have a no-arg constructor.
     * @throws ClassNotFoundException if class not found.
     */
    public void registerDSP(String className)
            throws DapException
    {
        try {
            Class klass = DSPFactory.class.getClassLoader().loadClass(className);
            registerDSP(klass);
        } catch (ClassNotFoundException e) {
            throw new DapException(e);
        }
    }

    /**
     * Register a DSP class.
     *
     * @param klass Class that implements DSP.
     * @throws IllegalAccessException if class is not accessible.
     * @throws InstantiationException if class doesnt have a no-arg constructor.
     * @throws ClassCastException     if class doesnt implement DSP interface.
     */
    public void registerDSP(Class klass)
    {
        registerDSP(klass, false);
    }

    /**
     * Register a DSP class.
     *
     * @param klass Class that implements DSP.
     * @param last  true=>insert at the end of the list; otherwise front
     * @throws IllegalAccessException if class is not accessible.
     * @throws InstantiationException if class doesnt have a no-arg constructor.
     * @throws ClassCastException     if class doesnt implement DSP interface.
     */
    synchronized public void registerDSP(Class klass, boolean last)
    {
        // is this already defined?
        for(int i = 0; i < dspRegistry.size(); i++) {
            if(dspRegistry.get(i).dspclass == klass)
                return; // already in registry
        }
        if(last)
            dspRegistry.add(new Registration(klass));
        else
            dspRegistry.add(0, new Registration(klass));
    }

    /**
     * See if a specific DSP is registered
     *
     * @param klass Class for which to search
     */

    synchronized public boolean dspRegistered(Class klass)
    {
        for(int i = 0; i < dspRegistry.size(); i++) {
            if(dspRegistry.get(i).dspclass == klass)
                return true;
        }
        return false;
    }

    /**
     * Unregister dsp.
     *
     * @param klass Class for which to search
     */
    synchronized public void dspUnregister(Class klass)
    {
        for(int i = 0; i < dspRegistry.size(); i++) {
            if(dspRegistry.get(i).dspclass == klass) {
                dspRegistry.remove(i);
                break;
            }
        }
    }

    /**
     * @param path
     * @return DSP object that can process this path
     * @throws DapException
     */

    synchronized public DSP
    findMatchingDSP(String path)
            throws DapException
    {
        for(int i = 0; i < dspRegistry.size(); i++) {
            try {
                Registration tester = dspRegistry.get(i);
                boolean ismatch = (Boolean) tester.matcher.invoke(null, path, (DapContext) null);
                if(ismatch) {
                    DSP dsp = (DSP) tester.dspclass.newInstance();
                    return dsp;
                }
            } catch (Exception e) {
                throw new DapException(e);
            }
        }
        throw new IllegalArgumentException("Cannot open " + path);
    }

} // DSPFactory

