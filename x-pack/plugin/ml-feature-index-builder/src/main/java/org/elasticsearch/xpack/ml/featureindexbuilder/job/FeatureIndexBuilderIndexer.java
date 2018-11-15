/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.ml.featureindexbuilder.job;

import org.apache.log4j.Logger;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xpack.core.indexing.AsyncTwoPhaseIndexer;
import org.elasticsearch.xpack.core.indexing.IndexerState;
import org.elasticsearch.xpack.core.indexing.IterationResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.xpack.ml.featureindexbuilder.persistence.DataframeIndex.DOC_TYPE;

public abstract class FeatureIndexBuilderIndexer extends AsyncTwoPhaseIndexer<Map<String, Object>, FeatureIndexBuilderJobStats> {

    private static final String COMPOSITE_AGGREGATION_NAME = "_data_frame";
    private static final Logger logger = Logger.getLogger(FeatureIndexBuilderIndexer.class.getName());
    private FeatureIndexBuilderJob job;

    public FeatureIndexBuilderIndexer(Executor executor, FeatureIndexBuilderJob job, AtomicReference<IndexerState> initialState,
            Map<String, Object> initialPosition) {
        super(executor, initialState, initialPosition, new FeatureIndexBuilderJobStats());

        this.job = job;
    }

    @Override
    protected String getJobId() {
        return job.getConfig().getId();
    }

    @Override
    protected void onStartJob(long now) {
    }

    @Override
    protected IterationResult<Map<String, Object>> doProcess(SearchResponse searchResponse) {
        final CompositeAggregation agg = searchResponse.getAggregations().get(COMPOSITE_AGGREGATION_NAME);
        return new IterationResult<>(processBucketsToIndexRequests(agg).collect(Collectors.toList()), agg.afterKey(),
                agg.getBuckets().isEmpty());
    }

    /*
     * Parses the result and creates a stream of indexable documents
     *
     * Implementation decisions:
     *
     * Extraction uses generic maps as intermediate exchange format in order to hook in ingest pipelines/processors
     * in later versions, see {@link IngestDocument).
     */
    private Stream<IndexRequest> processBucketsToIndexRequests(CompositeAggregation agg) {
        String indexName = job.getConfig().getDestinationIndex();
        List<CompositeValuesSourceBuilder<?>> sources = job.getConfig().getSourceConfig().getSources();
        Collection<AggregationBuilder> aggregationBuilders = job.getConfig().getAggregationConfig().getAggregatorFactories();

        return AggregationResultUtils.extractCompositeAggregationResults(agg, sources, aggregationBuilders).map(document -> {
            XContentBuilder builder;
            try {
                builder = jsonBuilder();
                builder.map(document);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            IndexRequest request = new IndexRequest(indexName, DOC_TYPE).source(builder);
            return request;
        });
    }

    @Override
    protected SearchRequest buildSearchRequest() {
        final Map<String, Object> position = getPosition();

        QueryBuilder queryBuilder = new MatchAllQueryBuilder();
        SearchRequest searchRequest = new SearchRequest(job.getConfig().getIndexPattern());

        List<CompositeValuesSourceBuilder<?>> sources = job.getConfig().getSourceConfig().getSources();

        CompositeAggregationBuilder compositeAggregation = new CompositeAggregationBuilder(COMPOSITE_AGGREGATION_NAME, sources);
        compositeAggregation.size(1000);

        if (position != null) {
            compositeAggregation.aggregateAfter(position);
        }

        for (AggregationBuilder agg : job.getConfig().getAggregationConfig().getAggregatorFactories()) {
            compositeAggregation.subAggregation(agg);
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.aggregation(compositeAggregation);
        sourceBuilder.size(0);
        sourceBuilder.query(queryBuilder);
        searchRequest.source(sourceBuilder);

        return searchRequest;
    }
}