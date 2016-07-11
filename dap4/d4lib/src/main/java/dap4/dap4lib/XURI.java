/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib;

import dap4.core.util.DapUtil;
import dap4.core.util.Escape;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provide an extended form of URI parser that can handle
 * multiple protocols and can parse the query and fragment parts.
 */

public class XURI
{

    //////////////////////////////////////////////////
    // Constants
    static final String QUERYSEP = "&";
    static final String FRAGMENTSEP = "&";

    // Define assembly flags

    static public enum Parts
    {
        FORMAT, // format protocol
        BASE, // base protocol
        PWD,  // including user
        HOST, // including port
        PATH,
        QUERY,
        FRAG;
    }

    // Mnemonics
    static public final EnumSet<Parts> URLONLY = EnumSet.of(Parts.BASE, Parts.PWD, Parts.HOST, Parts.PATH);
    static public final EnumSet<Parts> URLALL = EnumSet.of(Parts.FORMAT, Parts.BASE, Parts.PWD, Parts.HOST, Parts.PATH, Parts.QUERY, Parts.FRAG);
    static public final EnumSet<Parts> URLBASE = EnumSet.of(Parts.BASE, Parts.PWD, Parts.HOST, Parts.PATH, Parts.QUERY, Parts.FRAG);

    //////////////////////////////////////////////////
    // Instance variables

    protected String originaluri = null;
    //protected String trueurl = null;  // without the query or frag and with proper single protocol
    protected URI url = null; //applied to trueurl
    protected boolean isfile = false;

    protected String baseprotocol = null; // rightmost protocol
    protected String formatprotocol = null; // leftmost protocol
    protected String userinfo = null;
    protected String host = null;
    protected String path = null;
    protected String query = null;
    protected String frag = null;

    // Following are url decoded
    protected Map<String, String> queryfields // decomposed query
            = new HashMap<String, String>();
    protected Map<String, String> fragfields // decomposed fragment
            = new HashMap<String, String>();

    //////////////////////////////////////////////////
    // Constructor

    public XURI(String xurl)
            throws URISyntaxException
    {
        if(xurl == null)
            throw new URISyntaxException(xurl, "Null URI");
        // save the original uri
        this.originaluri = xurl;
        // The uri may be multi-protocol: e.g. dap4:file:...
        // Additionally, this may be a windows path, so it
        // will look like it has a single character protocol
        // that is really the drive letter.

        int[] breakpoint = new int[1];
        List<String> protocols = DapUtil.getProtocols(xurl, breakpoint); // should handle drive letters also
        String remainder = xurl.substring(breakpoint[0], xurl.length());
        switch (protocols.size()) {
        case 0: // pretend it is a file
            this.formatprotocol = "file";
            this.baseprotocol = "file";
            break;
        case 1:
            this.formatprotocol = protocols.get(0);
            this.baseprotocol = "http";   // default conversion
            break;
        case 2:
            this.baseprotocol = protocols.get(0);
            this.formatprotocol = protocols.get(1);
            break;
        default:
            throw new URISyntaxException(xurl, "Too many protocols: at most 2 allowed");
        }
        // Construct a usable url and parse it
        this.url = new URI(baseprotocol + ":" + remainder);

        // Extract the parts of the uri so they can
        // be modified and later reassembled

        if(!this.baseprotocol.equals(canonical(this.url.getScheme())))
            throw new URISyntaxException(this.url.toString(),
                    String.format("malformed url: %s :: %s",
                            this.baseprotocol, this.url.getScheme()));
        this.isfile = "file".equals(this.url.getScheme());
        this.userinfo = canonical(this.url.getUserInfo());
        /*
        if(this.isfile && DapUtil.hasDriveLetter(this.url.getHost() + ":")) {
            this.host = null;
            this.path = this.url.getHost() + ":";
            this.path = canonical(this.path + this.url.getPath());
        } else
         */
        {
            this.host = canonical(this.url.getHost());
            if(this.url.getPort() > 0)
                this.host += (":" + this.url.getPort());
            this.path = canonical(this.isfile?this.url.getSchemeSpecificPart()
                                             :this.url.getPath());
        }

        // Parse the raw query (before decoding)
        this.query = this.url.getRawQuery();
        if(this.query != null)
            setQuery(this.query);

        // Parse the raw fragment (before decoding)
        this.frag = canonical(this.url.getFragment());
        if(this.frag != null)
            setFragment(this.frag);
    }

    //////////////////////////////////////////////////
    // Accessors

    public String getOriginal()
    {
        return originaluri;
    }

    public String getBaseProtocol()
    {
        return baseprotocol;
    }

    public String getFormatProtocol()
    {
        return this.formatprotocol;
    }

    public void setBaseProtocol(String base)
    {
        this.baseprotocol = base;
    }

    public boolean isFile()
    {
        return this.isfile;
    }

    public String getUserinfo()
    {
        return this.userinfo;
    }

    public String getHost()
    {
        return this.host;
    }

    public String getPath()
    {
        return this.path;
    }

    public String getQuery()
    {
        return this.query;
    }

    public String getFrag()
    {
        return this.frag;
    }

    public Map<String, String> getQueryFields()
    {
        return this.queryfields;
    }

    public Map<String, String> getFragFields()
    {
        return this.fragfields;
    }

    public XURI
    setQuery(String q)
    {
        if(q == null || q.length() == 0) return this;
        String[] params = q.split(QUERYSEP);
        if(params != null && params.length > 0) {
            this.query = q;
            for(String param : params) {
                String[] pair = param.split("[=]");
                String name = Escape.urlDecode(pair[0]);
                name = name.toLowerCase(); // for consistent lookup
                String value = "";
                if(pair.length > 1) {
                    value = Escape.urlDecode(pair[1]);
                    this.queryfields.put(name, value);
                }
            }
        }
        return this;
    }

    public XURI
    setFragment(String f)
    {
        if(f == null || f.length() == 0) return this;
        String[] params = f.split(FRAGMENTSEP);
        if(params != null && params.length > 0) {
            this.frag = f;
            for(String param : params) {
                String[] pair = param.split("=");
                String name = Escape.urlDecode(pair[0]);
                name = name.toLowerCase(); // for consistent lookup
                String value = (pair.length == 2 ? Escape.urlDecode(pair[1])
                        : "");
                this.fragfields.put(name, value);
            }
        }
        return this;
    }

    //////////////////////////////////////////////////
    // API

    /**
     * Reassemble the url using the specified parts
     *
     * @param parts to include
     * @return the assembled uri
     */

    public String
    assemble(EnumSet<Parts> parts)
    {
        StringBuilder uri = new StringBuilder();
        // Note that format and base may be same, so case it out
        boolean neither = (!parts.contains(Parts.FORMAT) && !parts.contains(Parts.BASE));
        if(neither) {
            boolean both = (parts.contains(Parts.FORMAT) && parts.contains(Parts.BASE));
            if(both || parts.contains(Parts.FORMAT))
                uri.append(this.formatprotocol + ":");
            if(both || parts.contains(Parts.BASE)) {
                if(!this.baseprotocol.equals(this.formatprotocol))
                    uri.append(this.formatprotocol + ":");
            }
            if(this.baseprotocol.equals("file")) uri.append("/"); // use only single /
            else uri.append("//");
        }
        if(userinfo != null && parts.contains(Parts.PWD))
            uri.append(this.userinfo + ":");
        if(this.host != null && parts.contains(Parts.HOST))
            uri.append(this.host);
        if(this.path != null && parts.contains(Parts.PATH))
            uri.append(this.path);
        if(this.query != null && parts.contains(Parts.QUERY))
            uri.append("?" + this.query);
        if(this.frag != null && parts.contains(Parts.FRAG))
            uri.append("#" + this.frag);
        return uri.toString();
    }


    /**
     * Canonicalize a part of a URL
     *
     * @param s part of the url
     */
    static public String
    canonical(String s)
    {
        if(s != null) {
            s = s.trim();
            if(s.length() == 0)
                s = null;
        }
        return s;
    }

    public String toString()
    {
        return originaluri;
    }
}

