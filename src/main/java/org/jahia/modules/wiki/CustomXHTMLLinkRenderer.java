/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2020 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.wiki;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jcr.RepositoryException;

import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.render.RenderContext;
import org.slf4j.Logger;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.rendering.listener.Attachment;
import org.xwiki.rendering.listener.Link;
import org.xwiki.rendering.listener.LinkType;
import org.xwiki.rendering.parser.AttachmentParser;
import org.xwiki.rendering.renderer.LinkLabelGenerator;
import org.xwiki.rendering.renderer.printer.XHTMLWikiPrinter;
import org.xwiki.rendering.renderer.xhtml.XHTMLLinkRenderer;
import org.xwiki.rendering.wiki.WikiModel;

/**
 * User: ktlili
 * Date: Dec 7, 2009
 * Time: 4:26:00 PM
 */

/**
 * Basic default implementation to be used when the XWiki Rendering is used standalone, outside of XWiki.
 *
 * @version $Id$
 * @since 2.0M1
 */
@org.xwiki.component.annotation.Component
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class CustomXHTMLLinkRenderer implements XHTMLLinkRenderer, Initializable {

    /**
     * The XHTML element <code>class</code> parameter.
     */
    private static final String CLASS = "class";

    /**
     * The name of the XHTML format element.
     */
    private static final String SPAN = "span";

    /**
     * The link reference prefix indicating that the link is targeting an attachment.
     */
    private static final String ATTACH = "attach:";

    /**
     * The class attribute 'wikilink'.
     */
    private static final String WIKILINK = "wikilink";

    /**
     * The XHTML printer to use to output links as XHTML.
     */
    private XHTMLWikiPrinter xhtmlPrinter;

    /**
     * @see #setHasLabel(boolean)
     */
    private boolean hasLabel;

    /**
     * Used to generate the link targeting a local document.
     */
    private WikiModel wikiModel;

    /**
     * Used to generate a link label.
     */
    @Requirement
    private LinkLabelGenerator linkLabelGenerator;

    /**
     * Used to extract the attachment information form the reference if the link is targeting an attachment.
     */
    @Requirement
    private AttachmentParser attachmentParser;

    @Requirement
    private ComponentManager componentManager;

    private RenderContext renderContext;
    private String linkref = "";
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(CustomXHTMLLinkRenderer.class);

    /**
     * {@inheritDoc}
     *
     * @see org.xwiki.component.phase.Initializable#initialize()
     */
    public void initialize() throws InitializationException {
        // Try to find a WikiModel implementation and set it if it can be found. If not it means we're in
        // non wiki mode (i.e. no attachment in wiki documents and no links to documents for example).
        try {
            this.wikiModel = this.componentManager.lookup(WikiModel.class);
        } catch (Exception e) {
            // There's no WikiModel implementation available. this.wikiModel stays null.
        }
    }

    /**
     * Get renderContext
     *
     * @return
     */
    public RenderContext getRenderContext() {
        return renderContext;
    }

    /**
     * Set renderContext
     *
     * @param renderContext
     */
    public void setRenderContext(RenderContext renderContext) {
        this.renderContext = renderContext;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.xwiki.rendering.renderer.xhtml.XHTMLLinkRenderer#setHasLabel(boolean)
     */
    public void setHasLabel(boolean hasLabel) {
        this.hasLabel = hasLabel;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.xwiki.rendering.renderer.xhtml.XHTMLLinkRenderer#setXHTMLWikiPrinter(XHTMLWikiPrinter)
     */
    public void setXHTMLWikiPrinter(XHTMLWikiPrinter printer) {
        this.xhtmlPrinter = printer;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.xwiki.rendering.renderer.xhtml.XHTMLLinkRenderer#getXHTMLWikiPrinter()
     */
    public XHTMLWikiPrinter getXHTMLWikiPrinter() {
        return this.xhtmlPrinter;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.xwiki.rendering.renderer.xhtml.XHTMLLinkRenderer#beginLink(Link, boolean, Map)
     */
    public void beginLink(Link link, boolean isFreeStandingURI, Map<String, String> parameters) {
        linkref = link.getReference();
        // For files or reference, we don't create new node
        if (linkref != null && !linkref.startsWith("/") && !linkref.startsWith("http://") && !linkref.startsWith("https://")) {
            link.setReference(JCRContentUtils.generateNodeName(link.getReference(), 32));
        }
        if (linkref != null && (linkref.startsWith("http://") || linkref.startsWith("https://"))) {
            beginExternalLink(link, isFreeStandingURI, parameters);
        } else {
            beginInternalLink(link, isFreeStandingURI, parameters);
        }
    }

    /**
     * Start of an external link.
     *
     * @param link              the link definition (the reference)
     * @param isFreeStandingURI if true then the link is a free standing URI directly in the text
     * @param parameters        a generic list of parameters. Example: style="background-color: blue"
     */
    private void beginExternalLink(Link link, boolean isFreeStandingURI, Map<String, String> parameters)
    {
        Map<String, String> spanAttributes = new LinkedHashMap<String, String>();
        Map<String, String> aAttributes = new LinkedHashMap<String, String>();

        // Add all parameters to the A attributes
        aAttributes.putAll(parameters);

        spanAttributes.put(CLASS, "wikiexternallink");
        if (isFreeStandingURI) {
            aAttributes.put(CLASS, "wikimodel-freestanding");
        }

        // href attribute
        if (link.getType() == LinkType.INTERWIKI) {
            // TODO: Resolve the Interwiki link
        } else if (StringUtils.isEmpty(link.getReference())) {
            renderAutoLink(link, spanAttributes, aAttributes);
        } else {
            if (this.wikiModel != null && link.getType() == LinkType.URI && link.getReference().startsWith(ATTACH)) {
                // use the default attachment syntax parser to extract document name and attachment name
                Attachment attachment = this.attachmentParser.parse(link.getReference().substring(ATTACH.length()));
                aAttributes.put(HREF, this.wikiModel.getAttachmentURL(attachment.getDocumentName(),
                    attachment.getAttachmentName()));
            } else {
                aAttributes.put(HREF, link.getReference());
            }
        }

        getXHTMLWikiPrinter().printXMLStartElement(SPAN, spanAttributes);
        getXHTMLWikiPrinter().printXMLStartElement(ANCHOR, aAttributes);
    }

    /**
     * Start of an internal link.
     *
     * @param link              the link definition (the reference)
     * @param isFreeStandingURI if true then the link is a free standing URI directly in the text
     * @param parameters        a generic list of parameters. Example: style="background-color: blue"
     */
    private void beginInternalLink(Link link, boolean isFreeStandingURI, Map<String, String> parameters) {
        Map<String, String> spanAttributes = new LinkedHashMap<String, String>();
        Map<String, String> aAttributes = new LinkedHashMap<String, String>();

        // Add all parameters to the A attributes
        aAttributes.putAll(parameters);

        if (StringUtils.isEmpty(link.getReference())) {
            renderAutoLink(link, spanAttributes, aAttributes);
        } else if (this.wikiModel != null && this.wikiModel.isDocumentAvailable(link.getReference())) {
            spanAttributes.put(CLASS, WIKILINK);
            aAttributes.put(HREF, this.wikiModel.getDocumentViewURL(link.getReference(), link.getAnchor(),
                    link.getQueryString()));
        } else {
            // The wiki document doesn't exist
                // modified in order to be compatible with jahia
                String s = link.getReference().startsWith("/")?"":".html";
                if(pageExist(link)){
                    aAttributes.put(CLASS, "wikidef");
                }else{
                    if (!linkref.equals("")) {
                        try {
                            s += "?wikiTitle=" + URLEncoder.encode(linkref,"UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                   aAttributes.put(CLASS, "wikidef-new");
                }
                aAttributes.put(HREF, link.getReference() + s);
        }

        getXHTMLWikiPrinter().printXMLStartElement(SPAN, spanAttributes);
        getXHTMLWikiPrinter().printXMLStartElement(ANCHOR, aAttributes);
    }

    /**
     * @param link           the link definition (the reference)
     * @param spanAttributes the span element where to put the class
     * @param aAttributes    the anchor element where to put the reference
     */
    private void renderAutoLink(Link link, Map<String, String> spanAttributes, Map<String, String> aAttributes) {
        spanAttributes.put(CLASS, WIKILINK);

        StringBuilder buffer = new StringBuilder();
        if (link.getQueryString() != null) {
            buffer.append('?');
            buffer.append(link.getQueryString());
        }
        buffer.append('#');
        if (link.getAnchor() != null) {
            buffer.append(link.getAnchor());
        }

        aAttributes.put(HREF, buffer.toString());
    }

    /**
     * Method rewritten in  order to be compatible with Jahia
     * {@inheritDoc}
     *
     * @see org.xwiki.rendering.renderer.xhtml.XHTMLLinkRenderer#endLink(Link, boolean, Map)
     */
    public void endLink(Link link, boolean isFreeStandingURI, Map<String, String> parameters) {

        // If there was no link content then generate it based on the passed reference
        if (!this.hasLabel) {
            getXHTMLWikiPrinter().printXMLStartElement(SPAN, new String[][]{{CLASS, "wikigeneratedlinkcontent"}});
            if (link.getType() == LinkType.DOCUMENT) {
                getXHTMLWikiPrinter().printXML(this.linkLabelGenerator.generate(link));
            } else {
                getXHTMLWikiPrinter().printXML(linkref);

            }
            getXHTMLWikiPrinter().printXMLEndElement(SPAN);
        }

        getXHTMLWikiPrinter().printXMLEndElement(ANCHOR);
        getXHTMLWikiPrinter().printXMLEndElement(SPAN);
    }


    /**
     * Retrun true if page exist
     *
     * @param link
     * @return
     */
    private boolean pageExist(Link link) {
        try {
            return link.getReference().startsWith("/") || renderContext.getMainResource().getNode().getParent().hasNode(link.getReference());
        } catch (RepositoryException e) {
            return false;
        }
    }
}


