/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.media.jsonp.server;

import java.nio.charset.Charset;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Flow;
import io.helidon.webserver.BaseStreamWriter;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import static io.helidon.media.common.ContentTypeCharset.determineCharset;

/**
 * Class JsonArrayStreamWriter.
 */
public class JsonArrayStreamWriter<T> extends BaseStreamWriter<T> {

    private final DataChunk beginChunk;
    private final DataChunk separatorChunk;
    private final DataChunk endChunk;

    public JsonArrayStreamWriter(ServerRequest request, ServerResponse response, Class<T> type) {
        super(request, response, type);
        Charset charset = determineCharset(request.headers());
        beginChunk = DataChunk.create("[".getBytes(charset));
        separatorChunk = DataChunk.create(",".getBytes(charset));
        endChunk = DataChunk.create("]".getBytes(charset));
    }

    @Override
    public Flow.Publisher<DataChunk> apply(Flow.Publisher<T> publisher) {
        return new JsonArrayStreamPublisher(publisher);
    }

    class JsonArrayStreamPublisher implements Flow.Publisher<DataChunk>, Flow.Subscriber<T> {

        private long itemsRequested;
        private boolean first = true;
        private Flow.Subscriber<? super DataChunk> chunkSubscriber;
        private final Flow.Publisher<T> itemPublisher;
        private Flow.Subscription itemSubscription;

        JsonArrayStreamPublisher(Flow.Publisher<T> itemPublisher) {
            this.itemPublisher = itemPublisher;
        }

        // -- Publisher<DataChunk> --------------------------------------------

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> chunkSubscriber) {
            this.chunkSubscriber = chunkSubscriber;
            this.chunkSubscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    itemsRequested = n;
                    itemPublisher.subscribe(JsonArrayStreamPublisher.this);
                }

                @Override
                public void cancel() {
                    if (itemSubscription != null) {
                        itemSubscription.cancel();
                    }
                    itemsRequested = 0;
                }
            });

        }

        // -- Subscriber<T> ---------------------------------------------------

        @Override
        public void onSubscribe(Flow.Subscription itemSubscription) {
            this.itemSubscription = itemSubscription;
            chunkSubscriber.onNext(beginChunk);
            itemSubscription.request(itemsRequested);
        }

        @Override
        public void onNext(T item) {
            if (!first) {
                chunkSubscriber.onNext(separatorChunk);
            } else {
                first = false;
            }

            Flow.Publisher<DataChunk> itemChunkPublisher = getResponse().createPublisherUsingWriter(item);
            if (itemChunkPublisher == null) {
                throw new RuntimeException("Unable to find publisher for item " + item);
            }
            itemChunkPublisher.subscribe(new Flow.Subscriber<DataChunk>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(DataChunk item) {
                    chunkSubscriber.onNext(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    chunkSubscriber.onError(throwable);
                }

                @Override
                public void onComplete() {
                    // no-op
                }
            });
        }

        @Override
        public void onError(Throwable throwable) {
            chunkSubscriber.onNext(endChunk);
        }

        @Override
        public void onComplete() {
            chunkSubscriber.onNext(endChunk);
            chunkSubscriber.onComplete();
        }
    }
}