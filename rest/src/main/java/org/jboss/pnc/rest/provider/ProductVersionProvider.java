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
package org.jboss.pnc.rest.provider;

import com.google.common.collect.Sets;
import org.jboss.pnc.model.BuildConfigurationSet;
import org.jboss.pnc.model.ProductVersion;
import org.jboss.pnc.rest.provider.collection.CollectionInfo;
import org.jboss.pnc.rest.restmodel.BuildConfigurationSetRest;
import org.jboss.pnc.rest.restmodel.ProductVersionRest;
import org.jboss.pnc.rest.validation.exceptions.ValidationException;
import org.jboss.pnc.spi.datastore.repositories.BuildConfigurationSetRepository;
import org.jboss.pnc.spi.datastore.repositories.PageInfoProducer;
import org.jboss.pnc.spi.datastore.repositories.ProductVersionRepository;
import org.jboss.pnc.spi.datastore.repositories.SortInfoProducer;
import org.jboss.pnc.spi.datastore.repositories.api.RSQLPredicateProducer;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jboss.pnc.spi.datastore.predicates.ProductVersionPredicates.withBuildConfigurationId;
import static org.jboss.pnc.spi.datastore.predicates.ProductVersionPredicates.withProductId;

@Stateless
public class ProductVersionProvider extends AbstractProvider<ProductVersion, ProductVersionRest> {

    private BuildConfigurationSetRepository buildConfigurationSetRepository;

    @Inject
    public ProductVersionProvider(ProductVersionRepository productVersionRepository, BuildConfigurationSetRepository buildConfigurationSetRepository,
            RSQLPredicateProducer rsqlPredicateProducer, SortInfoProducer sortInfoProducer, PageInfoProducer pageInfoProducer) {
        super(productVersionRepository, rsqlPredicateProducer, sortInfoProducer, pageInfoProducer);
        this.buildConfigurationSetRepository = buildConfigurationSetRepository;
    }

    // needed for EJB/CDI
    public ProductVersionProvider() {
    }

    public CollectionInfo<ProductVersionRest> getAllForProduct(int pageIndex, int pageSize, String sortingRsql, String query,
            Integer productId){
        return queryForCollection(pageIndex, pageSize, sortingRsql, query, withProductId(productId));
    }

    public CollectionInfo<ProductVersionRest> getAllForBuildConfiguration(int pageIndex, int pageSize, String sortingRsql, String query,
            Integer buildConfigurationId){
        return queryForCollection(pageIndex, pageSize, sortingRsql, query, withBuildConfigurationId(buildConfigurationId));
    }

    public void updateBuildConfigurationSets(Integer id, List<BuildConfigurationSetRest> bcsRestModels) throws ValidationException {
        ProductVersion productVersion = repository.queryById(id);


        Set<BuildConfigurationSet> bcsDbModels = bcsRestModels.stream().map(restModel -> {
            BuildConfigurationSet dbModel = buildConfigurationSetRepository.queryById(restModel.getId());
            dbModel.setProductVersion(productVersion);
            return buildConfigurationSetRepository.save(dbModel);
        }).collect(Collectors.toSet());

        Sets.difference(productVersion.getBuildConfigurationSets(), bcsDbModels).forEach(x -> {
            x.setProductVersion(null);
            buildConfigurationSetRepository.save(x);
        });

        productVersion.setBuildConfigurationSets(bcsDbModels);

        repository.save(productVersion);



//        ProductVersionRest version = getSpecific(id);
//        version.setBuildConfigurationSets(buildConfigurationSetRests);
//
//        // TODO validate before saving
//
//        repository.save(toDBModel().apply(version));
    }

    @Override
    protected Function<? super ProductVersion, ? extends ProductVersionRest> toRESTModel() {
        return productVersion -> new ProductVersionRest(productVersion);
    }

    @Override
    protected Function<? super ProductVersionRest, ? extends ProductVersion> toDBModel() {
        return productVersionRest -> productVersionRest.toDBEntityBuilder().build();        
    }

}
