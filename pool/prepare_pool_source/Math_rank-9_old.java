package com.fasterxml.jackson.dataformat.xml.deser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * {@link JsonParser} implementation that exposes XML structure as
 * set of JSON events that can be used for data binding.
 */
public class FromXmlParser
    extends ParserMinimalBase
{
    /**
     * Enumeration that defines all togglable features for XML parsers
     */
    public enum Feature {
        DUMMY_PLACEHOLDER(false)
        ;

        final boolean _defaultState;
        final int _mask;
        
        /**
         * Method that calculates bit set (flags) of all features that
         * are enabled by default.
         */
        public static int collectDefaults()
        {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }
        
        private Feature(boolean defaultState) {
            _defaultState = defaultState;
            _mask = (1 << ordinal());
        }
        
        public boolean enabledByDefault() { return _defaultState; }
        public int getMask() { return _mask; }
    }

    /**
     * In cases where a start element has both attributes and non-empty textual
     * value, we have to create a bogus property; we will use this as
     * the property name.
     */
    protected final static String UNNAMED_TEXT_PROPERTY = "";

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    /**
     * Bit flag composed of bits that indicate which
     * {@link FromXmlParser.Feature}s
     * are enabled.
     */
    protected int _xmlFeatures;
    
    protected ObjectCodec _objectCodec;

    /*
    /**********************************************************
    /* I/O state
    /**********************************************************
     */

    /**
     * Flag that indicates whether parser is closed or not. Gets
     * set when parser is either closed by explicit call
     * ({@link #close}) or when end-of-input is reached.
     */
    protected boolean _closed;
    
    final protected IOContext _ioContext;

    /*
    /**********************************************************
    /* Parsing state
    /**********************************************************
     */
    
    /**
     * Information about parser context, context in which
     * the next token is to be parsed (root, array, object).
     */
    protected XmlReadContext _parsingContext;
    
    protected final XmlTokenStream _xmlTokens;

    /**
     * We need special handling to keep track of whether a value
     * may be exposed as simple leaf value.
     */
    protected boolean _mayBeLeaf;

    protected JsonToken _nextToken;

    protected String _currText;

    protected Set<String> _namesToWrap;
    
    /*
    /**********************************************************
    /* Parsing state, parsed values
    /**********************************************************
     */
    
    /**
     * ByteArrayBuilder is needed if 'getBinaryValue' is called. If so,
     * we better reuse it for remainder of content.
     */
    protected ByteArrayBuilder _byteArrayBuilder = null;

    /**
     * We will hold on to decoded binary data, for duration of
     * current event, so that multiple calls to
     * {@link #getBinaryValue} will not need to decode data more
     * than once.
     */
    protected byte[] _binaryValue;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */
    
    public FromXmlParser(IOContext ctxt, int genericParserFeatures, int xmlFeatures,
            ObjectCodec codec, XMLStreamReader xmlReader)
    {
        super(genericParserFeatures);
        _xmlFeatures = xmlFeatures;
        _ioContext = ctxt;
        _objectCodec = codec;
        _parsingContext = XmlReadContext.createRootContext(-1, -1);
        // and thereby start a scope
        _nextToken = JsonToken.START_OBJECT;
        _xmlTokens = new XmlTokenStream(xmlReader, ctxt.getSourceReference());
    }

    @Override
    public ObjectCodec getCodec() {
        return _objectCodec;
    }

    @Override
    public void setCodec(ObjectCodec c) {
        _objectCodec = c;
    }

    /**
     * XML format does require support from custom {@link ObjectCodec}
     * (that is, {@link XmlMapper}), so need to return true here.
     * 
     * @return True since XML format does require support from codec
     */
    @Override
    public boolean requiresCustomCodec() {
        return false;
    }
    
    /*
    /**********************************************************
    /* Extended API, configuration
    /**********************************************************
     */

    public FromXmlParser enable(Feature f) {
        _xmlFeatures |= f.getMask();
        return this;
    }

    public FromXmlParser disable(Feature f) {
        _xmlFeatures &= ~f.getMask();
        return this;
    }

    public final boolean isEnabled(Feature f) {
        return (_xmlFeatures & f.getMask()) != 0;
    }

    public FromXmlParser configure(Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    /*
    /**********************************************************
    /* Extended API, access to some internal components
    /**********************************************************
     */

    /**
     * Method that allows application direct access to underlying
     * Stax {@link XMLStreamWriter}. Note that use of writer is
     * discouraged, and may interfere with processing of this writer;
     * however, occasionally it may be necessary.
     *<p>
     * Note: writer instance will always be of type
     * {@link org.codehaus.stax2.XMLStreamWriter2} (including
     * Typed Access API) so upcasts are safe.
     */
    public XMLStreamReader getStaxReader() {
        return _xmlTokens.getXmlReader();
    }

    /*
    /**********************************************************
    /* Internal API
    /**********************************************************
     */

    /**
     * Method that may be called to indicate that specified names
     * (only local parts retained currently: this may be changed in
     * future) should be considered "auto-wrapping", meaning that
     * they will be doubled to contain two opening elements, two
     * matching closing elements. This is needed for supporting
     * handling of so-called "unwrapped" array types, something
     * XML mappings like JAXB often use.
     *<p>
     * NOTE: this method is considered part of internal implementation
     * interface, and it is <b>NOT</b> guaranteed to remain unchanged
     * between minor versions (it is however expected not to change in
     * patch versions). So if you have to use it, be prepared for
     * possible additional work.
     * 
     * @since 2.1
     */
    public void addVirtualWrapping(Set<String> namesToWrap)
    {
//System.out.println("AddWraps: "+namesToWrap+" (current name="+_parsingContext.getCurrentName()+")");        

        /* 17-Sep-2012, tatu: Not 100% sure why, but this is necessary to avoid
         *   problems with Lists-in-Lists properties
         */
        String name = _xmlTokens.getLocalName();
        if (name != null && namesToWrap.contains(name)) {
//System.out.println("!!! AddWraps matches CURRENT name... flash repeat; nextToken="+_nextToken);
            _xmlTokens.repeatStartElement();
        }
        _namesToWrap = namesToWrap;
        _parsingContext.setNamesToWrap(namesToWrap);
    }

    /*
    /**********************************************************
    /* JsonParser impl
    /**********************************************************
     */
    
    /**
     * Method that can be called to get the name associated with
     * the current event.
     */
    @Override
    public String getCurrentName()
        throws IOException, JsonParseException
    {
        // [JACKSON-395]: start markers require information from parent
        String name;
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            XmlReadContext parent = _parsingContext.getParent();
            name = parent.getCurrentName();
        } else {
            name = _parsingContext.getCurrentName();
        }
        // sanity check
        if (name == null) {
            throw new IllegalStateException("Missing name, in state: "+_currToken);
        }
        return name;
    }

    @Override
    public void overrideCurrentName(String name)
    {
        // Simple, but need to look for START_OBJECT/ARRAY's "off-by-one" thing:
        XmlReadContext ctxt = _parsingContext;
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            ctxt = ctxt.getParent();
        }
        ctxt.setCurrentName(name);
    }
    
    @Override
    public void close() throws IOException
    {
        if (!_closed) {
            _closed = true;
            try {
                if (_ioContext.isResourceManaged() || isEnabled(JsonParser.Feature.AUTO_CLOSE_SOURCE)) {
                    _xmlTokens.closeCompletely();
                } else {
                    _xmlTokens.close();
                }
            } finally {
                // as per [JACKSON-324], do in finally block
                // Also, internal buffer(s) can now be released as well
                _releaseBuffers();
            }
        }
    }

    @Override
    public boolean isClosed() { return _closed; }

    @Override
    public XmlReadContext getParsingContext()
    {
        return _parsingContext;
    }

    /**
     * Method that return the <b>starting</b> location of the current
     * token; that is, position of the first character from input
     * that starts the current token.
     */
    @Override
    public JsonLocation getTokenLocation()
    {
        return _xmlTokens.getTokenLocation();
    }

    /**
     * Method that returns location of the last processed character;
     * usually for error reporting purposes
     */
    @Override
    public JsonLocation getCurrentLocation()
    {
        return _xmlTokens.getCurrentLocation();
    }

    /**
     * Since xml representation can not really distinguish between array
     * and object starts (both are represented with elements), this method
     * is overridden and taken to mean that expecation is that the current
     * start element is to mean 'start array', instead of default of
     * 'start object'.
     */
    @Override
    public boolean isExpectedStartArrayToken()
    {
        JsonToken t = _currToken;
        if (t == JsonToken.START_OBJECT) {        	
            _currToken = JsonToken.START_ARRAY;
            // Ok: must replace current context with array as well
            _parsingContext.convertToArray();
//System.out.println(" isExpectedArrayStart: OBJ->Array, wraps now: "+_parsingContext.getNamesToWrap());
            // And just in case a field name was to be returned, wipe it
            _nextToken = null;
            // and last thing, [Issue#33], better ignore attributes
            _xmlTokens.skipAttributes();
            return true;
        }
//System.out.println(" isExpectedArrayStart?: t="+t);
        return (t == JsonToken.START_ARRAY);
    }

    // DEBUGGING
/*
    @Override
    public JsonToken nextToken() throws IOException, JsonParseException
    {
        JsonToken t = nextToken0();
        if (t != null) {
            switch (t) {
            case FIELD_NAME:
                System.out.println("JsonToken: FIELD_NAME '"+_parsingContext.getCurrentName()+"'");
                break;
            case VALUE_STRING:
                System.out.println("JsonToken: VALUE_STRING '"+getText()+"'");
                break;
            default:
                System.out.println("JsonToken: "+t);
            }
        }
        return t;
    } 
    */

    @Override
    public JsonToken nextToken() throws IOException, JsonParseException
    {
        _binaryValue = null; // to fix [Issue-29]
        if (_nextToken != null) {
            JsonToken t = _nextToken;
            _currToken = t;
            _nextToken = null;
            switch (t) {
            case START_OBJECT:
                _parsingContext = _parsingContext.createChildObjectContext(-1, -1);
                break;
            case START_ARRAY:
                _parsingContext = _parsingContext.createChildArrayContext(-1, -1);
                break;
            case END_OBJECT:
            case END_ARRAY:
                _parsingContext = _parsingContext.getParent();
                _namesToWrap = _parsingContext.getNamesToWrap();
                break;
            case FIELD_NAME:
                _parsingContext.setCurrentName(_xmlTokens.getLocalName());
                break;
            default: // VALUE_STRING, VALUE_NULL
                // should be fine as is?
            }
            return t;
        }
        int token = _xmlTokens.next();
        
        /* Need to have a loop just because we may have to eat/convert
         * a start-element that indicates an array element.
         */
        while (token == XmlTokenStream.XML_START_ELEMENT) {
            // If we thought we might get leaf, no such luck
            if (_mayBeLeaf) {
                // leave _mayBeLeaf set, as we start a new context
                _nextToken = JsonToken.FIELD_NAME;
                _parsingContext = _parsingContext.createChildObjectContext(-1, -1);
                return (_currToken = JsonToken.START_OBJECT);
            }
            if (_parsingContext.inArray()) {
                /* Yup: in array, so this element could be verified; but it won't be reported
                 * anyway, and we need to process following event.
                 */
                token = _xmlTokens.next();
                _mayBeLeaf = true;
                continue;
            }
            String name = _xmlTokens.getLocalName();
            _parsingContext.setCurrentName(name);

            /* Ok: virtual wrapping can be done by simply repeating
             * current START_ELEMENT. Couple of ways to do it; but
             * start by making _xmlTokens replay the thing...
             */
            if (_namesToWrap != null && _namesToWrap.contains(name)) {
                _xmlTokens.repeatStartElement();
            }

            _mayBeLeaf = true;
            /* Ok: in array context we need to skip reporting field names. But what's the best way
             * to find next token?
             */
            return (_currToken = JsonToken.FIELD_NAME);
        }

        // Ok; beyond start element, what do we get?
        switch (token) {
        case XmlTokenStream.XML_END_ELEMENT:
            // Simple, except that if this is a leaf, need to suppress end:
            if (_mayBeLeaf) {
                _mayBeLeaf = false;
                return (_currToken = JsonToken.VALUE_NULL);
            }
            _currToken = _parsingContext.inArray() ? JsonToken.END_ARRAY : JsonToken.END_OBJECT;
            _parsingContext = _parsingContext.getParent();
            _namesToWrap = _parsingContext.getNamesToWrap();
            return _currToken;
            
        case XmlTokenStream.XML_ATTRIBUTE_NAME:
            // If there was a chance of leaf node, no more...
            if (_mayBeLeaf) {
                _mayBeLeaf = false;
                _nextToken = JsonToken.FIELD_NAME;
                _currText = _xmlTokens.getText();
                _parsingContext = _parsingContext.createChildObjectContext(-1, -1);
                return (_currToken = JsonToken.START_OBJECT);
            }
            _parsingContext.setCurrentName(_xmlTokens.getLocalName());
            return (_currToken = JsonToken.FIELD_NAME);
        case XmlTokenStream.XML_ATTRIBUTE_VALUE:
            _currText = _xmlTokens.getText();
            return (_currToken = JsonToken.VALUE_STRING);
        case XmlTokenStream.XML_TEXT:
            _currText = _xmlTokens.getText();
            if (_mayBeLeaf) {
                _mayBeLeaf = false;
                // Also: must skip following END_ELEMENT
                _xmlTokens.skipEndElement();
                /* One more refinement (pronunced like "hack") is that if
                 * we had an empty String (or all white space), and we are
                 * deserializing an array, we better just hide the text
                 * altogether.
                 */
                if (_parsingContext.inArray()) {
                    if (_isEmpty(_currText)) {
                        _currToken = JsonToken.END_ARRAY;
                        _parsingContext = _parsingContext.getParent();
                        _namesToWrap = _parsingContext.getNamesToWrap();
                        return _currToken;
                    }
                }
                return (_currToken = JsonToken.VALUE_STRING);
            }
            // If not a leaf, need to transform into property...
            _parsingContext.setCurrentName(UNNAMED_TEXT_PROPERTY);
            _nextToken = JsonToken.VALUE_STRING;
            return (_currToken = JsonToken.FIELD_NAME);
        case XmlTokenStream.XML_END:
            return (_currToken = null);
        }
        
        // should never get here
        _throwInternal();
        return null;
    }
    
    /*
    /**********************************************************
    /* Public API, access to token information, text
    /**********************************************************
     */

    @Override
    public String getText() throws IOException, JsonParseException
    {
        if (_currToken == null) {
            return null;
        }
        switch (_currToken) {
        case FIELD_NAME:
            return getCurrentName();
        case VALUE_STRING:
            return _currText;
        default:
            return _currToken.asString();
        }
    }

    @Override
    public char[] getTextCharacters() throws IOException, JsonParseException {
        String text = getText();
        return (text == null)  ? null : text.toCharArray();
    }

    @Override
    public int getTextLength() throws IOException, JsonParseException {
        String text = getText();
        return (text == null)  ? 0 : text.length();
    }

    @Override
    public int getTextOffset() throws IOException, JsonParseException {
        return 0;
    }

    /**
     * XML input actually would offer access to character arrays; but since
     * we must coalesce things it cannot really be exposed.
     */
    @Override
    public boolean hasTextCharacters()
    {
        return false;
    }

    /*
    /**********************************************************
    /* Public API, access to token information, binary
    /**********************************************************
     */

    @Override
    public Object getEmbeddedObject() throws IOException, JsonParseException {
        // no way to embed POJOs for now...
        return null;
    }

    @Override
    public byte[] getBinaryValue(Base64Variant b64variant)
        throws IOException, JsonParseException
    {
        if (_currToken != JsonToken.VALUE_STRING &&
                (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT || _binaryValue == null)) {
            _reportError("Current token ("+_currToken+") not VALUE_STRING or VALUE_EMBEDDED_OBJECT, can not access as binary");
        }
        /* To ensure that we won't see inconsistent data, better clear up
         * state...
         */
        if (_binaryValue == null) {
            try {
                _binaryValue = _decodeBase64(b64variant);
            } catch (IllegalArgumentException iae) {
                throw _constructError("Failed to decode VALUE_STRING as base64 ("+b64variant+"): "+iae.getMessage());
            }
        }        
        return _binaryValue;
    }
    
    protected byte[] _decodeBase64(Base64Variant b64variant)
        throws IOException, JsonParseException
    {
        ByteArrayBuilder builder = _getByteArrayBuilder();
    
        final String str = getText();
        int ptr = 0;
        int len = str.length();

        main_loop:
        while (ptr < len) {
            // first, we'll skip preceding white space, if any
            char ch;
            do {
                ch = str.charAt(ptr++);
                if (ptr >= len) {
                    break main_loop;
                }
            } while (ch <= INT_SPACE);
            int bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                _reportInvalidBase64(b64variant, ch, 0, null);
            }
            int decodedData = bits;
            // then second base64 char; can't get padding yet, nor ws
            if (ptr >= len) {
                _reportBase64EOF();
            }
            ch = str.charAt(ptr++);
            bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                _reportInvalidBase64(b64variant, ch, 1, null);
            }
            decodedData = (decodedData << 6) | bits;
            // third base64 char; can be padding, but not ws
            if (ptr >= len) {
                _reportBase64EOF();
            }
            ch = str.charAt(ptr++);
            bits = b64variant.decodeBase64Char(ch);

            // First branch: can get padding (-> 1 byte)
            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    _reportInvalidBase64(b64variant, ch, 2, null);
                }
                // Ok, must get padding
                if (ptr >= len) {
                    _reportBase64EOF();
                }
                ch = str.charAt(ptr++);
                if (!b64variant.usesPaddingChar(ch)) {
                    _reportInvalidBase64(b64variant, ch, 3, "expected padding character '"+b64variant.getPaddingChar()+"'");
                }
                // Got 12 bits, only need 8, need to shift
                decodedData >>= 4;
                builder.append(decodedData);
                continue;
            }
            // Nope, 2 or 3 bytes
            decodedData = (decodedData << 6) | bits;
            // fourth and last base64 char; can be padding, but not ws
            if (ptr >= len) {
                _reportBase64EOF();
            }
            ch = str.charAt(ptr++);
            bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    _reportInvalidBase64(b64variant, ch, 3, null);
                }
                decodedData >>= 2;
                builder.appendTwoBytes(decodedData);
            } else {
                // otherwise, our triple is now complete
                decodedData = (decodedData << 6) | bits;
                builder.appendThreeBytes(decodedData);
            }
        }
        return builder.toByteArray();
    }

    /**
     * @param bindex Relative index within base64 character unit; between 0
     *   and 3 (as unit has exactly 4 characters)
     */
    @Override
    protected void _reportInvalidBase64(Base64Variant b64variant, char ch, int bindex, String msg)
        throws JsonParseException
    {
        String base;
        if (ch <= INT_SPACE) {
            base = "Illegal white space character (code 0x"+Integer.toHexString(ch)+") as character #"+(bindex+1)+" of 4-char base64 unit: can only used between units";
        } else if (b64variant.usesPaddingChar(ch)) {
            base = "Unexpected padding character ('"+b64variant.getPaddingChar()+"') as character #"+(bindex+1)+" of 4-char base64 unit: padding only legal as 3rd or 4th character";
        } else if (!Character.isDefined(ch) || Character.isISOControl(ch)) {
            // Not sure if we can really get here... ? (most illegal xml chars are caught at lower level)
            base = "Illegal character (code 0x"+Integer.toHexString(ch)+") in base64 content";
        } else {
            base = "Illegal character '"+ch+"' (code 0x"+Integer.toHexString(ch)+") in base64 content";
        }
        if (msg != null) {
            base = base + ": " + msg;
        }
        throw new JsonParseException(base, JsonLocation.NA);
    }

    @Override
    protected void _reportBase64EOF()
        throws JsonParseException
    {
        throw new JsonParseException("Unexpected end-of-String when base64 content", JsonLocation.NA);
    }
    
    /*
    /**********************************************************
    /* Numeric accessors
    /**********************************************************
     */

    @Override
    public BigInteger getBigIntegerValue() throws IOException, JsonParseException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BigDecimal getDecimalValue() throws IOException, JsonParseException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public double getDoubleValue() throws IOException, JsonParseException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getFloatValue() throws IOException, JsonParseException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getIntValue() throws IOException, JsonParseException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getLongValue() throws IOException, JsonParseException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public NumberType getNumberType() throws IOException, JsonParseException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Number getNumberValue() throws IOException, JsonParseException
    {
        // TODO Auto-generated method stub
        return null;
    }

    /*
    /**********************************************************
    /* Abstract method impls for stuff from JsonParser
    /**********************************************************
     */

    /**
     * Method called when an EOF is encountered between tokens.
     * If so, it may be a legitimate EOF, but only iff there
     * is no open non-root context.
     */
    @Override
    protected void _handleEOF() throws JsonParseException
    {
        if (!_parsingContext.inRoot()) {
            _reportInvalidEOF(": expected close marker for "+_parsingContext.getTypeDesc()+" (from "+_parsingContext.getStartLocation(_ioContext.getSourceReference())+")");
        }
    }
    
    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    /**
     * Method called to release internal buffers owned by the base
     * parser.
     */
    protected void _releaseBuffers() throws IOException
    {
        // anything we can/must release? Underlying parser should do all of it, for now?
    }

    protected ByteArrayBuilder _getByteArrayBuilder()
    {
        if (_byteArrayBuilder == null) {
            _byteArrayBuilder = new ByteArrayBuilder();
        } else {
            _byteArrayBuilder.reset();
        }
        return _byteArrayBuilder;
    }

    protected boolean _isEmpty(String str)
    {
        int len = (str == null) ? 0 : str.length();
        if (len > 0) {
            for (int i = 0; i < len; ++i) {
                if (str.charAt(i) > ' ') {
                    return false;
                }
            }
        }
        return true;
    }
}
