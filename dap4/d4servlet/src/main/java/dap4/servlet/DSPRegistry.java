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
 */

public class DSPRegistry
{
    //////////////////////////////////////////////////
    // Constants

    static public final String MATCHMETHOD = "dspMatch";

    //////////////////////////////////////////////////
    // Type Decls

    static protected class Registration
    {
        Class<? extends DSP> dspclass;
        Method matcher;

        public Registration(Class<? extends DSP> cl)
        {
            this.dspclass = cl;
            try {
                this.matcher = dspclass.getMethod(MATCHMETHOD, String.class, DapContext.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("DSPFactory: no " + MATCHMETHOD + " method for DSP: " + dspclass.getName());
            }
        }
    }

    //////////////////////////////////////////////////
    // Instance Variables

    /**
     * Define a map of known DSP classes.
     * Must be ordered to allow control over
     * test order
     */
    protected List<Registration> registry = new ArrayList<>();

    //////////////////////////////////////////////////
    // Constructor(s)

    public DSPRegistry()
    {
    }


    //////////////////////////////////////////////////
    // API

    /**
     * Register a DSP, using its class string name.
     *
     * @param className Class that implements DSP.
     * @throws IllegalAccessException if class is not accessible.
     * @throws InstantiationException if class doesnt have a no-arg constructor.
     * @throws ClassNotFoundException if class not found.
     */
    synchronized public void register(String className, boolean last)
            throws DapException
    {
        try {
            Class<? extends DSP> klass = (Class<? extends DSP>) DapCache.class.getClassLoader().loadClass(className);
            register(klass, last);
        } catch (ClassNotFoundException e) {
            throw new DapException(e);
        }
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
    synchronized public void
    register(Class<? extends DSP> klass, boolean last)
    {
        // is this already defined?
        if(registered(klass)) return;
        if(last)
            registry.add(new Registration(klass));
        else
            registry.add(0, new Registration(klass));
    }

    /**
     * See if a specific DSP is registered
     *
     * @param klass Class for which to search
     */

    synchronized public boolean
    registered(Class<? extends DSP> klass)
    {
        for(Registration r : registry) {
            if(r.dspclass == klass) return true;
        }
        return false;
    }

    /**
     * Unregister dsp.
     *
     * @param klass Class for which to search
     */
    synchronized public void
    unregister(Class<? extends DSP> klass)
    {
        for(int i = 0; i < registry.size(); i++) {
            if(registry.get(i).dspclass == klass) {
                registry.remove(i);
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
        for(int i = 0; i < registry.size(); i++) {
            try {
                Registration tester = registry.get(i);
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

}

