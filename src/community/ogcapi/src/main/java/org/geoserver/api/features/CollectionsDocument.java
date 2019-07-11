/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.api.features;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.geoserver.api.APIRequestInfo;
import org.geoserver.api.AbstractDocument;
import org.geoserver.api.Link;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.config.GeoServer;
import org.geoserver.ows.URLMangler;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.platform.ServiceException;
import org.opengis.filter.Filter;
import org.springframework.http.MediaType;

/**
 * A class representing the WFS3 server "collections" in a way that Jackson can easily translate to
 * JSON/YAML (and can be used as a Freemarker template model)
 */
@JacksonXmlRootElement(localName = "Collections", namespace = "http://www.opengis.net/wfs/3.0")
@JsonPropertyOrder({"links", "collections"})
public class CollectionsDocument extends AbstractDocument {

    private final FeatureTypeInfo featureType;
    private final GeoServer geoServer;
    /* private final List<WFS3Extension> extensions; */

    public CollectionsDocument(GeoServer geoServer /*, List<WFS3Extension> extensions */) {
        this(geoServer, null /*, extensions */);
    }

    public CollectionsDocument(GeoServer geoServer, FeatureTypeInfo featureType
            /* List<WFS3Extension> extensions */ ) {
        this.geoServer = geoServer;
        this.featureType = featureType;
        /* this.extensions = extensions; */

        // build the links
        APIRequestInfo requestInfo = APIRequestInfo.get();
        String baseUrl = requestInfo.getBaseURL();
        boolean firstSelf = true;
        for (MediaType format :
                requestInfo.getProducibleMediaTypes(CollectionDocument.class, true)) {
            String path =
                    "ogc/features/collections/"
                            + (featureType != null ? NCNameResourceCodec.encode(featureType) : "");
            String apiUrl =
                    ResponseUtils.buildURL(
                            baseUrl,
                            path,
                            Collections.singletonMap("f", format.toString()),
                            URLMangler.URLType.SERVICE);
            String linkType = Link.REL_ALTERNATE;
            String linkTitle = "This document " + " as " + format;
            if (firstSelf && requestInfo.isFormatRequested(format)) {
                linkType = Link.REL_SELF;
                linkTitle = "This document";
            }
            links.add(new Link(apiUrl, linkType, format.toString(), linkTitle));
        }
    }

    @JacksonXmlProperty(localName = "Links")
    public List<Link> getLinks() {
        return links;
    }

    @JacksonXmlProperty(localName = "Collection")
    public Iterator<CollectionDocument> getCollections() {
        // single collection case
        if (featureType != null) {
            CollectionDocument document = new CollectionDocument(geoServer, featureType);
            /* decorateWithExtensions(document); */
            return Collections.singleton(document).iterator();
        }

        // full scan case
        CloseableIterator<FeatureTypeInfo> featureTypes =
                geoServer.getCatalog().list(FeatureTypeInfo.class, Filter.INCLUDE);
        return new Iterator<CollectionDocument>() {

            CollectionDocument next;

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }

                boolean hasNext = featureTypes.hasNext();
                if (!hasNext) {
                    featureTypes.close();
                    return false;
                } else {
                    try {
                        FeatureTypeInfo featureType = featureTypes.next();
                        CollectionDocument collection =
                                new CollectionDocument(geoServer, featureType);
                        /* decorateWithExtensions(collection); */

                        next = collection;
                        return true;
                    } catch (Exception e) {
                        featureTypes.close();
                        throw new ServiceException(
                                "Failed to iterate over the feature types in the catalog", e);
                    }
                }
            }

            @Override
            public CollectionDocument next() {
                CollectionDocument result = next;
                this.next = null;
                return result;
            }
        };
    }

    /*
    private void decorateWithExtensions(CollectionDocument collection) {
        if (extensions != null) {
            for (WFS3Extension extension : extensions) {
                extension.extendCollection(collection, request);
            }
        }
    }
    */

}
