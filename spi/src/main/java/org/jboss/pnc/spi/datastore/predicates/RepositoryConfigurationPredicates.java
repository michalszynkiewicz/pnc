/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
 */
package org.jboss.pnc.spi.datastore.predicates;

import org.jboss.pnc.common.util.StringUtils;
import org.jboss.pnc.model.RepositoryConfiguration;
import org.jboss.pnc.model.RepositoryConfiguration_;
import org.jboss.pnc.spi.datastore.repositories.api.Predicate;

public class RepositoryConfigurationPredicates {

    public static Predicate<RepositoryConfiguration> withExactInternalScmRepoUrl(String internalUrl) {
        return (root, query, cb) -> cb.equal(root.get(RepositoryConfiguration_.internalUrl), internalUrl);
    }

    public static Predicate<RepositoryConfiguration> withInternalScmRepoUrl(String internalUrl) {
        String internalUrlStripped = StringUtils.stripProtocol(internalUrl);
        internalUrlStripped = StringUtils.stripSuffix(internalUrlStripped, ".git");

        String pattern = "%" + internalUrlStripped + "%";

        return (root, query, cb) -> cb.like(root.get(RepositoryConfiguration_.internalUrl), pattern);
    }

    public static Predicate<RepositoryConfiguration> withExternalScmRepoUrl(String externalScmRepoUrl) {
        String internalUrlStripped = StringUtils.stripProtocol(externalScmRepoUrl);
        internalUrlStripped = StringUtils.stripSuffix(internalUrlStripped, ".git");

        String pattern = "%" + internalUrlStripped + "%";

        return (root, query, cb) -> cb.like(root.get(RepositoryConfiguration_.externalUrl), pattern);
    }

    public static Predicate<RepositoryConfiguration> searchByScmUrl(String scmUrl) {
        String urlStripped = StringUtils.stripProtocol(scmUrl);
        urlStripped = StringUtils.stripSuffix(urlStripped, ".git");

        String pattern = "%" + urlStripped + "%";

        return (root, query, cb) -> cb.or(
                cb.like(root.get(RepositoryConfiguration_.internalUrl), pattern),
                cb.like(root.get(RepositoryConfiguration_.externalUrl), pattern));
    }
}