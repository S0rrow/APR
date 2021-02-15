/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ode.bpel.compiler;

import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.wsdl.Definition;
import javax.wsdl.Import;
import javax.wsdl.Message;
import javax.wsdl.PortType;
import javax.wsdl.Types;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.compiler.api.CompilationException;
import org.apache.ode.bpel.compiler.api.CompilerContext;
import org.apache.ode.bpel.compiler.bom.PartnerLinkType;
import org.apache.ode.bpel.compiler.bom.PropertyAlias;
import org.apache.ode.bpel.compiler.wsdl.Definition4BPEL;
import org.apache.ode.bpel.compiler.wsdl.XMLSchemaType;
import org.apache.ode.utils.DOMUtils;
import org.apache.ode.utils.msg.MessageBundle;
import org.apache.ode.utils.xsd.SchemaModel;
import org.apache.ode.utils.xsd.SchemaModelImpl;
import org.apache.ode.utils.xsd.XSUtils;
import org.apache.ode.utils.xsd.XsdException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;


/**
 * A parsed collection of WSDL definitions, including BPEL-specific extensions.
 */
class WSDLRegistry {
    private static final Log __log = LogFactory.getLog(WSDLRegistry.class);

    private static final CommonCompilationMessages __cmsgs =
            MessageBundle.getMessages(CommonCompilationMessages.class);

    private final HashMap<String, ArrayList<Definition4BPEL>> _definitions = new HashMap<String, ArrayList<Definition4BPEL>>();

    private final Map<URI, byte[]> _schemas = new HashMap<URI,byte[]>();

    private SchemaModel _model;

    private CompilerContext _ctx;


    WSDLRegistry(CompilerContext cc) {
        // bogus schema to force schema creation
        _schemas.put(URI.create("http://fivesight.com/bogus/namespace"),
                ("<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\""
                        + " targetNamespace=\"http://fivesight.com/bogus/namespace\">"
                        + "<xsd:simpleType name=\"__bogusType__\">"
                        + "<xsd:restriction base=\"xsd:normalizedString\"/>"
                        + "</xsd:simpleType>" + "</xsd:schema>").getBytes());
        
        _ctx = cc;
    }

    public Definition4BPEL[] getDefinitions(){
        ArrayList<Definition4BPEL> result = new ArrayList<Definition4BPEL>();
        for (ArrayList<Definition4BPEL> definition4BPELs : _definitions.values()) {
            for (Definition4BPEL definition4BPEL : definition4BPELs) {
                result.add(definition4BPEL);
            }
        }
        return result.toArray(new Definition4BPEL[result.size()]);
    }

    /**
     * Get the schema model (XML Schema).
     *
     * @return schema model
     */
    public SchemaModel getSchemaModel() {
        if (_model == null) {
            _model = SchemaModelImpl.newModel(_schemas);
        }
        assert _model != null;
        return _model;
    }

    /**
     * Adds a WSDL definition for use in resolving MessageType, PortType,
     * Operation and BPEL properties and property aliases
     *
     * @param def WSDL definition
     */
    @SuppressWarnings("unchecked")
    public void addDefinition(Definition4BPEL def, ResourceFinder rf, URI defuri) throws CompilationException {
        if (def == null)
            throw new NullPointerException("def=null");

        if (__log.isDebugEnabled()) {
            __log.debug("addDefinition(" + def.getTargetNamespace() + " from " + def.getDocumentBaseURI() + ")");
        }

        if (_definitions.containsKey(def.getTargetNamespace())) {
            // This indicates that we imported a WSDL with the same namespace from
            // two different locations. This is not an error, but should be a warning.
            if (__log.isInfoEnabled()) {
                __log.info("WSDL at " + defuri + " is a duplicate import, your documents " +
                        "should all be in different namespaces (its's not nice but will still work).");
            }
        }

        ArrayList<Definition4BPEL> defs = null;
        if (_definitions.get(def.getTargetNamespace()) == null) defs = new ArrayList<Definition4BPEL>();
        else defs = _definitions.get(def.getTargetNamespace());

        defs.add(def);
        _definitions.put(def.getTargetNamespace(), defs);

        captureSchemas(def, rf, defuri);

        if (__log.isDebugEnabled())
            __log.debug("Processing <imports> in " + def.getDocumentBaseURI());

        for (List<Import>  imports : ((Map<String, List<Import>>)def.getImports()).values()) {
            HashSet<String> imported = new HashSet<String>();

            for (Import im : imports) {
                // If there are several imports in the same WSDL all importing the same namespace
                // that is a sure sign of programmer error.
                if (imported.contains(im.getNamespaceURI())) {
                    if (__log.isInfoEnabled()) {
                        __log.info("WSDL at " + im.getLocationURI() + " imports several documents in the same " +
                                "namespace (" + im.getNamespaceURI() + "), your documents should all be in different " +
                                "namespaces (its's not nice but will still work).");
                    }
                }

                Definition4BPEL importDef = (Definition4BPEL) im.getDefinition();

                // The assumption here is that if the definition is not set on the
                // import object then there was some problem parsing the thing,
                // although it would have been nice to actually get the parse
                // error.
                if (importDef == null) {
                    CompilationException ce = new CompilationException(
                            __cmsgs.errWsdlImportNotFound(im.getNamespaceURI(),
                                    im.getLocationURI()).setSource(new SourceLocationImpl(defuri)));
                    if (_ctx == null)
                        throw ce;

                    _ctx.recoveredFromError(new SourceLocationImpl(defuri), ce);

                    continue;
                }

                imported.add(im.getNamespaceURI());
                addDefinition((Definition4BPEL) im.getDefinition(), rf, defuri.resolve(im.getLocationURI()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void captureSchemas(Definition def, ResourceFinder rf, URI defuri) throws CompilationException {
        assert def != null;

        if (__log.isDebugEnabled())
            __log.debug("Processing XSD schemas in " + def.getDocumentBaseURI());

        Types types = def.getTypes();

        if (types != null) {
            for (Iterator<ExtensibilityElement> iter =
                    ((List<ExtensibilityElement>)def.getTypes().getExtensibilityElements()).iterator();
                 iter.hasNext();) {
                ExtensibilityElement ee = iter.next();

                
                if (ee instanceof XMLSchemaType) {
                    String schema = ((XMLSchemaType)ee).getXMLSchema();
                    Map<URI, byte[]> capture = null;

                    WsdlFinderXMLEntityResolver resolver = new WsdlFinderXMLEntityResolver(rf, defuri);
                    try {
                        
                        capture = XSUtils.captureSchema(defuri, schema, resolver);

                        // Add new schemas to our list.
                        _schemas.putAll(capture);

                        try {
                            Document doc = DOMUtils.parse(new InputSource(new StringReader(schema)));
                            String schemaTargetNS = doc.getDocumentElement().getAttribute("targetNamespace");
                            if (schemaTargetNS != null && schemaTargetNS.length() > 0)
                                resolver.addInternalResource(new URI(schemaTargetNS), schema);
                        } catch (Exception e) {
                            throw new RuntimeException("Couldn't parse schema in " + def.getTargetNamespace(), e);
                        }
                    } catch (XsdException xsde) {
                        __log.debug("captureSchemas: capture failed for " + defuri,xsde);

                        LinkedList<XsdException> exceptions = new LinkedList<XsdException>();
                        while (xsde != null)  {
                            exceptions.addFirst(xsde);
                            xsde = xsde.getPrevious();
                        }

                        for (XsdException ex : exceptions) {
                            // TODO: the line number here is going to be wrong for the in-line schema.
                            String location = ex.getSystemId() + ":"  + ex.getLineNumber();
                            CompilationException ce = new CompilationException(
                                    __cmsgs.errSchemaError(ex.getDetailMessage()).setSource(new SourceLocationImpl(defuri)));
                            if (_ctx != null)
                                _ctx.recoveredFromError(new SourceLocationImpl(defuri),ce);
                            else
                                throw ce;
                        }
                    }
                    // invalidate model
                    _model = null;

                }
            }
        }
    }

    public org.apache.ode.bpel.compiler.bom.Property getProperty(QName name) {
        ArrayList<Definition4BPEL> defs = _definitions.get(name.getNamespaceURI());
        if (defs == null) return null;
        for (Definition4BPEL definition4BPEL : defs) {
            if (definition4BPEL != null && definition4BPEL.getProperty(name) != null)
                return definition4BPEL.getProperty(name);
        }
        return null;
    }

    public PropertyAlias getPropertyAlias(QName propertyName, QName messageType) {
        ArrayList<Definition4BPEL> defs = _definitions.get(propertyName.getNamespaceURI());
        if (defs == null) return null;
        for (Definition4BPEL definition4BPEL : defs) {
            if (definition4BPEL != null && definition4BPEL.getPropertyAlias(propertyName, messageType) != null)
                return definition4BPEL.getPropertyAlias(propertyName, messageType);
        }
        return null;
    }

    public PartnerLinkType getPartnerLinkType(QName partnerLinkType) {
        ArrayList<Definition4BPEL> defs = _definitions.get(partnerLinkType.getNamespaceURI());
        if (defs == null) return null;
        for (Definition4BPEL definition4BPEL : defs) {
            if (definition4BPEL != null && definition4BPEL.getPartnerLinkType(partnerLinkType) != null)
                return definition4BPEL.getPartnerLinkType(partnerLinkType);
        }
        return null;
    }

    public PortType getPortType(QName portType) {
        ArrayList<Definition4BPEL> defs = _definitions.get(portType.getNamespaceURI());
        if (defs == null) return null;
        for (Definition4BPEL definition4BPEL : defs) {
            if (definition4BPEL != null && definition4BPEL.getPortType(portType) != null)
                return definition4BPEL.getPortType(portType);
        }
        return null;
    }

    public Message getMessage(QName msgType) {
        ArrayList<Definition4BPEL> defs = _definitions.get(msgType.getNamespaceURI());
        if (defs == null) return null;
        for (Definition4BPEL definition4BPEL : defs) {
            if (definition4BPEL != null && definition4BPEL.getMessage(msgType) != null)
                return definition4BPEL.getMessage(msgType);
        }
        return null;
    }

}