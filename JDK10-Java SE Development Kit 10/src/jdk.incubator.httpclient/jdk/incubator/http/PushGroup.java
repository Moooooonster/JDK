/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package jdk.incubator.http;

import java.security.AccessControlContext;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import jdk.incubator.http.HttpResponse.BodyHandler;
import jdk.incubator.http.HttpResponse.UntrustedBodyHandler;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Log;

/**
 * One PushGroup object is associated with the parent Stream of the pushed
 * Streams. This keeps track of all common state associated with the pushes.
 */
class PushGroup<U,T> {
    // the overall completion object, completed when all pushes are done.
    final CompletableFuture<Void> resultCF;
    final CompletableFuture<Void> noMorePushesCF;

    volatile Throwable error; // any exception that occurred during pushes

    // CF for main response
    final CompletableFuture<HttpResponse<T>> mainResponse;

    // user's subscriber object
    final HttpResponse.MultiSubscriber<U, T> multiSubscriber;

    final HttpResponse.BodyHandler<T> mainBodyHandler;

    private final AccessControlContext acc;

    int numberOfPushes;
    int remainingPushes;
    boolean noMorePushes = false;

    PushGroup(HttpResponse.MultiSubscriber<U, T> multiSubscriber,
              HttpRequestImpl req,
              AccessControlContext acc) {
        this(multiSubscriber, req, new MinimalFuture<>(), acc);
    }

    // Check mainBodyHandler before calling nested constructor.
    private PushGroup(HttpResponse.MultiSubscriber<U, T> multiSubscriber,
                      HttpRequestImpl req,
                      CompletableFuture<HttpResponse<T>> mainResponse,
                      AccessControlContext acc) {
        this(multiSubscriber,
             mainResponse,
             multiSubscriber.onRequest(req),
             acc);
    }

    // This private constructor is called after all parameters have been checked.
    private PushGroup(HttpResponse.MultiSubscriber<U, T> multiSubscriber,
                      CompletableFuture<HttpResponse<T>> mainResponse,
                      HttpResponse.BodyHandler<T> mainBodyHandler,
                      AccessControlContext acc) {

        assert mainResponse != null; // A new instance is created above
        assert mainBodyHandler != null; // should have been checked above

        this.resultCF = new MinimalFuture<>();
        this.noMorePushesCF = new MinimalFuture<>();
        this.multiSubscriber = multiSubscriber;
        this.mainResponse = mainResponse.thenApply(r -> {
            multiSubscriber.onResponse(r);
            return r;
        });
        this.mainBodyHandler = mainBodyHandler;
        if (acc != null) {
            // Restricts the file publisher with the senders ACC, if any
            if (mainBodyHandler instanceof UntrustedBodyHandler)
                ((UntrustedBodyHandler)this.mainBodyHandler).setAccessControlContext(acc);
        }
        this.acc = acc;
    }

    CompletableFuture<Void> groupResult() {
        return resultCF;
    }

    HttpResponse.MultiSubscriber<U, T> subscriber() {
        return multiSubscriber;
    }

    Optional<BodyHandler<T>> handlerForPushRequest(HttpRequest ppRequest) {
        Optional<BodyHandler<T>> bh = multiSubscriber.onPushPromise(ppRequest);
        if (acc != null && bh.isPresent()) {
            // Restricts the file publisher with the senders ACC, if any
            BodyHandler<T> x = bh.get();
            if (x instanceof UntrustedBodyHandler)
                ((UntrustedBodyHandler)x).setAccessControlContext(acc);
            bh = Optional.of(x);
        }
        return bh;
    }

    HttpResponse.BodyHandler<T> mainResponseHandler() {
        return mainBodyHandler;
    }

    synchronized void setMainResponse(CompletableFuture<HttpResponse<T>> r) {
        r.whenComplete((HttpResponse<T> response, Throwable t) -> {
            if (t != null)
                mainResponse.completeExceptionally(t);
            else
                mainResponse.complete(response);
        });
    }

    synchronized void addPush() {
        numberOfPushes++;
        remainingPushes++;
    }

    // This is called when the main body response completes because it means
    // no more PUSH_PROMISEs are possible

    synchronized void noMorePushes(boolean noMore) {
        noMorePushes = noMore;
        checkIfCompleted();
        noMorePushesCF.complete(null);
    }

    CompletableFuture<Void> pushesCF() {
        return noMorePushesCF;
    }

    synchronized boolean noMorePushes() {
        return noMorePushes;
    }

    synchronized void pushCompleted() {
        remainingPushes--;
        checkIfCompleted();
    }

    synchronized void checkIfCompleted() {
        if (Log.trace()) {
            Log.logTrace("PushGroup remainingPushes={0} error={1} noMorePushes={2}",
                         remainingPushes,
                         (error==null)?error:error.getClass().getSimpleName(),
                         noMorePushes);
        }
        if (remainingPushes == 0 && error == null && noMorePushes) {
            if (Log.trace()) {
                Log.logTrace("push completed");
            }
            resultCF.complete(null);
        }
    }

    synchronized void pushError(Throwable t) {
        if (t == null) {
            return;
        }
        this.error = t;
        resultCF.completeExceptionally(t);
    }
}
