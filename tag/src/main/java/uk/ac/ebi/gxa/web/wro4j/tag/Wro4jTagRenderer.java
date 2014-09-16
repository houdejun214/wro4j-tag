/*
 * Copyright 2008-2011 Microarray Informatics Team, EMBL-European Bioinformatics Institute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * For further details of the Gene Expression Atlas project, including source code,
 * downloads and documentation, please see:
 *
 * http://gxa.github.com/gxa
 */
package uk.ac.ebi.gxa.web.wro4j.tag;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.weardex.core.resource.AntPathMatcher;
import com.weardex.core.resource.PathMatchingResourcePatternResolver;
import com.weardex.core.utils.PathUtils;
import com.weardex.core.utils.StringUtils;
import ro.isdc.wro.model.group.Group;
import ro.isdc.wro.model.resource.Resource;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

import static com.google.common.collect.Collections2.filter;
import static java.util.EnumSet.copyOf;

/**
 * @author alf
 */
public class Wro4jTagRenderer {
    private final GroupResolver groupResolver;
    private final Wro4jTagProperties properties;
    private final EnumSet<ResourceHtmlTag> tags;
    private final DirectoryLister lister;
    //private static final PathMatchingResourcePatternResolver pathResolver = new PathMatchingResourcePatternResolver();
    private static final Map<String, Collection<Resource>> resourcsCache = new WeakHashMap<>();


    public Wro4jTagRenderer(GroupResolver groupResolver, Wro4jTagProperties properties, EnumSet<ResourceHtmlTag> tags, DirectoryLister lister) {
        this.groupResolver = groupResolver;
        this.properties = properties;
        this.tags = copyOf(tags);
        this.lister = lister;
    }

    public void render(Writer writer, String name, String contextPath) throws IOException, Wro4jTagException {
        for (Resource resource : collectResources(groupResolver.getGroup(name))) {
            writer.write(render(contextPath, resource));
            writer.write("\n");
        }
    }

    private Collection<Resource> collectResources(final Group group) throws Wro4jTagException, IOException {
        return properties.isDebugOn() ? uncompressedResources( group) : compressedBundle(group);
    }

    private Collection<Resource> compressedBundle(Group group) throws Wro4jTagException, IOException {
        List<Resource> list = new ArrayList<Resource>();
        for (ResourceHtmlTag type : tags)
            if (group.hasResourcesOfType(type.getType()))
                list.add(resourceForBundle(group, type));
        return list;
    }

    private Collection<Resource> uncompressedResources(Group group) throws IOException {
        String key = StringUtils.join(this.tags,"_") + "@" + group.getName();
        Collection<Resource> allFiles = resourcsCache.get(key);
        if (allFiles == null) {
            Collection<Resource> files = filter(group.getResources(), new Predicate<Resource>() {
                public boolean apply(Resource resource) {
                    return isSupported(resource);
                }
            });
            // resolve the ant paths
            allFiles = Lists.newArrayList();
            for (Resource res : files) {
                for (String filename : lister.list(res.getUri())) {
                    allFiles.add(Resource.create(filename, res.getType()));
                }
            }
            resourcsCache.put(key, allFiles);
        }
        return allFiles;
    }

    private Resource resourceForBundle(Group group, ResourceHtmlTag tag) throws Wro4jTagException, IOException {
        final String template = properties.getNameTemplate().forGroup(group.getName(), tag);
        String path = properties.getResourcePath(tag.getType());
        for (String filename : lister.list(path)) {
            if (filename.matches(template)) {
                return Resource.create(ResourcePath.join(path, filename), tag.getType());
            }
        }
        throw new Wro4jTagException("No file matching the template: '" + template +
                "' found in the path: " + properties.getResourcePath(tag.getType()) +
                " - have you built the compressed versions properly?");
    }

    private String render(String contextPath, Resource resource){
        final String uri = ResourcePath.join(contextPath, resource.getUri());
        return ResourceHtmlTag.forType(resource.getType()).render(uri);
    }

    boolean isSupported(Resource resource) {
        for (ResourceHtmlTag tag : tags) {
            if (resource.getType() == tag.getType())
                return true;
        }
        return false;
    }

    static interface DirectoryLister {
        Collection<String> list(String path) throws IOException;
    }
}
