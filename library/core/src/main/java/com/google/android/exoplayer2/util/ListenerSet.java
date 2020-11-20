/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.util;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.common.base.Supplier;
import java.util.ArrayDeque;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.annotation.Nonnull;

/**
 * A set of listeners.
 *
 * <p>Events are guaranteed to arrive in the order in which they happened even if a new event is
 * triggered recursively from another listener.
 *
 * <p>Events are also guaranteed to be only sent to the listeners registered at the time the event
 * was enqueued and haven't been removed since.
 *
 * @param <T> The listener type.
 * @param <E> The {@link MutableFlags} type used to indicate which events occurred.
 */
public final class ListenerSet<T, E extends MutableFlags> {

  /**
   * An event sent to a listener.
   *
   * @param <T> The listener type.
   */
  public interface Event<T> {

    /** Invokes the event notification on the given listener. */
    void invoke(T listener);
  }

  /**
   * An event sent to a listener when all other events sent during one {@link Looper} message queue
   * iteration were handled by the listener.
   *
   * @param <T> The listener type.
   * @param <E> The {@link MutableFlags} type used to indicate which events occurred.
   */
  public interface IterationFinishedEvent<T, E extends MutableFlags> {

    /**
     * Invokes the iteration finished event.
     *
     * @param listener The listener to invoke the event on.
     * @param eventFlags The combined event flags of all events sent in this iteration.
     */
    void invoke(T listener, E eventFlags);
  }

  private static final int MSG_ITERATION_FINISHED = 0;

  private final Handler iterationFinishedHandler;
  private final Supplier<E> eventFlagsSupplier;
  private final IterationFinishedEvent<T, E> iterationFinishedEvent;
  private final CopyOnWriteArraySet<ListenerHolder<T, E>> listeners;
  private final ArrayDeque<Runnable> flushingEvents;
  private final ArrayDeque<Runnable> queuedEvents;

  private boolean released;

  /**
   * Creates a new listener set.
   *
   * @param looper A {@link Looper} used to call listeners on. The same {@link Looper} must be used
   *     to call all other methods of this class.
   * @param eventFlagsSupplier A {@link Supplier} for new instances of {@link E the event flags
   *     type}.
   * @param iterationFinishedEvent An {@link IterationFinishedEvent} sent when all other events sent
   *     during one {@link Looper} message queue iteration were handled by the listeners.
   */
  public ListenerSet(
      Looper looper,
      Supplier<E> eventFlagsSupplier,
      IterationFinishedEvent<T, E> iterationFinishedEvent) {
    this(
        /* listeners= */ new CopyOnWriteArraySet<>(),
        looper,
        eventFlagsSupplier,
        iterationFinishedEvent);
  }

  private ListenerSet(
      CopyOnWriteArraySet<ListenerHolder<T, E>> listeners,
      Looper looper,
      Supplier<E> eventFlagsSupplier,
      IterationFinishedEvent<T, E> iterationFinishedEvent) {
    this.listeners = listeners;
    this.eventFlagsSupplier = eventFlagsSupplier;
    this.iterationFinishedEvent = iterationFinishedEvent;
    flushingEvents = new ArrayDeque<>();
    queuedEvents = new ArrayDeque<>();
    // It's safe to use "this" because we don't send a message before exiting the constructor.
    @SuppressWarnings("methodref.receiver.bound.invalid")
    Handler handler = Util.createHandler(looper, this::handleIterationFinished);
    iterationFinishedHandler = handler;
  }

  /**
   * Copies the listener set.
   *
   * @param looper The new {@link Looper} for the copied listener set.
   * @param iterationFinishedEvent The new {@link IterationFinishedEvent} sent when all other events
   *     sent during one {@link Looper} message queue iteration were handled by the listeners.
   * @return The copied listener set.
   */
  @CheckResult
  public ListenerSet<T, E> copy(
      Looper looper, IterationFinishedEvent<T, E> iterationFinishedEvent) {
    return new ListenerSet<>(listeners, looper, eventFlagsSupplier, iterationFinishedEvent);
  }

  /**
   * Adds a listener to the set.
   *
   * <p>If a listener is already present, it will not be added again.
   *
   * @param listener The listener to be added.
   */
  public void add(T listener) {
    if (released) {
      return;
    }
    Assertions.checkNotNull(listener);
    listeners.add(new ListenerHolder<>(listener, eventFlagsSupplier));
  }

  /**
   * Removes a listener from the set.
   *
   * <p>If the listener is not present, nothing happens.
   *
   * @param listener The listener to be removed.
   */
  public void remove(T listener) {
    for (ListenerHolder<T, E> listenerHolder : listeners) {
      if (listenerHolder.listener.equals(listener)) {
        listenerHolder.release();
        listeners.remove(listenerHolder);
      }
    }
  }

  /**
   * Adds an event that is sent to the listeners when {@link #flushEvents} is called.
   *
   * @param eventFlag An integer indicating the type of the event, or {@link C#INDEX_UNSET} to not
   *     report this event with a flag.
   * @param event The event.
   */
  public void queueEvent(int eventFlag, Event<T> event) {
    CopyOnWriteArraySet<ListenerHolder<T, E>> listenerSnapshot =
        new CopyOnWriteArraySet<>(listeners);
    queuedEvents.add(
        () -> {
          for (ListenerHolder<T, E> holder : listenerSnapshot) {
            holder.invoke(eventFlag, event);
          }
        });
  }

  /** Notifies listeners of events previously enqueued with {@link #queueEvent(int, Event)}. */
  public void flushEvents() {
    if (queuedEvents.isEmpty()) {
      return;
    }
    if (!iterationFinishedHandler.hasMessages(MSG_ITERATION_FINISHED)) {
      iterationFinishedHandler.obtainMessage(MSG_ITERATION_FINISHED).sendToTarget();
    }
    boolean recursiveFlushInProgress = !flushingEvents.isEmpty();
    flushingEvents.addAll(queuedEvents);
    queuedEvents.clear();
    if (recursiveFlushInProgress) {
      // Recursive call to flush. Let the outer call handle the flush queue.
      return;
    }
    while (!flushingEvents.isEmpty()) {
      flushingEvents.peekFirst().run();
      flushingEvents.removeFirst();
    }
  }

  /**
   * {@link #queueEvent(int, Event) Queues} a single event and immediately {@link #flushEvents()
   * flushes} the event queue to notify all listeners.
   *
   * @param eventFlag An integer flag indicating the type of the event, or {@link C#INDEX_UNSET} to
   *     not report this event with a flag.
   * @param event The event.
   */
  public void sendEvent(int eventFlag, Event<T> event) {
    queueEvent(eventFlag, event);
    flushEvents();
  }

  /**
   * Releases the set of listeners.
   *
   * <p>This will ensure no events are sent to any listener after this method has been called.
   */
  public void release() {
    for (ListenerHolder<T, E> listenerHolder : listeners) {
      listenerHolder.release();
    }
    listeners.clear();
    released = true;
  }

  private boolean handleIterationFinished(Message message) {
    for (ListenerHolder<T, E> holder : listeners) {
      holder.iterationFinished(eventFlagsSupplier, iterationFinishedEvent);
      if (iterationFinishedHandler.hasMessages(MSG_ITERATION_FINISHED)) {
        // The invocation above triggered new events (and thus scheduled a new message). We need to
        // stop here because this new message will take care of informing every listener about the
        // new update (including the ones already called here).
        break;
      }
    }
    return true;
  }

  private static final class ListenerHolder<T, E extends MutableFlags> {

    @Nonnull public final T listener;

    private E eventsFlags;
    private boolean released;

    public ListenerHolder(@Nonnull T listener, Supplier<E> eventFlagSupplier) {
      this.listener = listener;
      this.eventsFlags = eventFlagSupplier.get();
    }

    public void release() {
      released = true;
    }

    public void invoke(int eventFlag, Event<T> event) {
      if (!released) {
        event.invoke(listener);
        if (eventFlag != C.INDEX_UNSET) {
          eventsFlags.add(eventFlag);
        }
      }
    }

    public void iterationFinished(
        Supplier<E> eventFlagSupplier, IterationFinishedEvent<T, E> event) {
      if (!released) {
        // Reset flags before invoking the listener to ensure we keep all new flags that are set by
        // recursive events triggered from this callback.
        E flagToNotify = eventsFlags;
        eventsFlags = eventFlagSupplier.get();
        event.invoke(listener, flagToNotify);
      }
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      return listener.equals(((ListenerHolder<?, ?>) other).listener);
    }

    @Override
    public int hashCode() {
      return listener.hashCode();
    }
  }
}
