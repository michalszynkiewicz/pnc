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
package org.jboss.pnc.datastore.limits;

import org.jboss.pnc.spi.datastore.repositories.api.SortInfo;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SortInfoTest {

    private final DefaultSortInfoProducer defaultSortInfoProducer = new DefaultSortInfoProducer();


    @Test
    public void shouldParseSimpleAscendingRSQL() throws Exception {
        //given
        String sorting = "sort=asc=id";

        //when
        SortInfo testedSorting = defaultSortInfoProducer.getSortInfo(sorting);

        //then
        assertThat(testedSorting.getFields()).containsExactly("id");
        assertThat(testedSorting.getDirection()).isEqualTo(SortInfo.SortingDirection.ASC);
    }

    @Test
    public void shouldParseSimpleDescendingRSQL() throws Exception {
        //given
        String sorting = "sort=desc=id";

        //when
        SortInfo testedSorting = defaultSortInfoProducer.getSortInfo(sorting);

        //then
        assertThat(testedSorting.getFields()).containsExactly("id");
        assertThat(testedSorting.getDirection()).isEqualTo(SortInfo.SortingDirection.DESC);
    }

    @Test
    public void shouldParseComplicatedAscendingRSQL() throws Exception {
        //given
        String sorting = "sort=asc=(id,name)";

        //when
        SortInfo testedSorting = defaultSortInfoProducer.getSortInfo(sorting);

        //then
        assertThat(testedSorting.getFields()).containsExactly("id", "name");
        assertThat(testedSorting.getDirection()).isEqualTo(SortInfo.SortingDirection.ASC);
    }

    @Test
    public void shouldParseComplicatedDescendingRSQL() throws Exception {
        //given
        String sorting = "sort=desc=(id,name)";

        //when
        SortInfo testedSorting = defaultSortInfoProducer.getSortInfo(sorting);

        //then
        assertThat(testedSorting.getFields()).containsExactly("id", "name");
        assertThat(testedSorting.getDirection()).isEqualTo(SortInfo.SortingDirection.DESC);
    }

    @Test
    public void shouldAllowSortingStringWithoutSortAtTheBeginning() throws Exception {
        //given
        String sorting = "=asc=(id,name)";

        //when
        SortInfo testedSorting = defaultSortInfoProducer.getSortInfo(sorting);

        //then
        assertThat(testedSorting.getFields()).containsExactly("id", "name");
        assertThat(testedSorting.getDirection()).isEqualTo(SortInfo.SortingDirection.ASC);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectWrongRSQL() throws Exception {
        //given
        String sorting = "Yeah, this is a wrong rsql";

        //when
        defaultSortInfoProducer.getSortInfo(sorting);
    }

}