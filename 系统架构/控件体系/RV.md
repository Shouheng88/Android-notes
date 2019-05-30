# RecyclerView 源码分析

RV 和 ListView：

1. 不同的地方
2. 高效的原因
3. 使用不便的地方

要分析的内容：

1. 基本的流程；
2. 一些效果的实现的逻辑是如何解耦出来的；
3. 缓存实现的原理。

RV 一个类的代码有超过 1W 行代码，首先我们要明确分析它的角度：

1. 布局发生变化的时候通过 `requestLayout()` 请求进行界面重绘，然后重走 `onMeasure()`, `onLayout()`, `onDraw()` 三个流程
2. 任何控件的绘制无外乎 `onMeasure()`, `onLayout()`, `onDraw()` 三个流程

## notifyXXX() 系列方法的更新原理

先来看下布局发生变化的时候的更新逻辑，当我们调用 Adapter 的 `notifyXXX()` 方法的时候，内部会调用一个 mObservable 对应的 `notifyXXX()` 方法：

```java
    public abstract static class Adapter<VH extends ViewHolder> {
        private final AdapterDataObservable mObservable = new AdapterDataObservable();

        // ...

        // 注册观察者
        public void registerAdapterDataObserver(@NonNull AdapterDataObserver observer) {
            mObservable.registerObserver(observer);
        }

        // 通知观察者
        public final void notifyItemChanged(int position, @Nullable Object payload) {
            mObservable.notifyItemRangeChanged(position, 1, payload);
        }
    }

    // 内部通过 ArrayList 维护了一个观察者列表
    static class AdapterDataObservable extends Observable<AdapterDataObserver> {
        // ...
    }
```

这里的 AdapterDataObservable 也是 Adapter 的内部类，继承了 `android.database.Observable`，这是典型的观察者模式的封装。`android.database.Observable` 内部维护了一个观察者列表，我们可以通过它提供的方法注册和取消注册观察者。当我们调用了 RV 的 `setAdapter()` 方法的时候，内部会调用 Adapter 的 `registerAdapterDataObserver()` 方法注册一个默认的观察者。这个观察者也是 RV 的内部类，即 RecyclerViewDataObserver：

```java
    private class RecyclerViewDataObserver extends AdapterDataObserver {
        // ...
        // 当数据发生变化的时候，这个方法被通知到，从而触发数据更新
        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            assertNotInLayoutOrScroll(null);
            if (mAdapterHelper.onItemRangeChanged(positionStart, itemCount, payload)) {
                triggerUpdateProcessor();
            }
        }

        void triggerUpdateProcessor() {
            if (POST_UPDATES_ON_ANIMATION && mHasFixedSize && mIsAttached) {
                ViewCompat.postOnAnimation(RecyclerView.this, mUpdateChildViewsRunnable);
            } else {
                mAdapterUpdateDuringMeasure = true;
                // 请求布局进行更新
                requestLayout();
            }
        }
    }
```

因此，当我们调用 Adapter 的 `notifyXXX()` 方法的时候，它会通知 Adapter 中注册的所有的观察者，其中就包括这个默认的观察者。当观察者收到数据变化的逻辑之后，就通过 `requestLayout()` 请求 RV 进行重绘，然后重走 `onMeasure()`, `onLayout()`, `onDraw()` 三个流程。

## 测量，布局和绘制的流程

首先，在 RV 的布局和测量等过程中，涉及到的一个数据结构是 State，它包含了当前 RV 的状态信息。State 通常被用来在 RV 中的各个组件之间传递信息。这个数据结构中定义了三个常量，用来表示 RV 当前处于哪个阶段；此外，它还定义了一个稀疏数组，用来让用户通过 remove/put/get 等操作存取一些信息：

```java
    public static class State {
        // 三个常量，表示三个不同的布局阶段
        static final int STEP_START = 1;
        static final int STEP_LAYOUT = 1 << 1;
        static final int STEP_ANIMATIONS = 1 << 2;

        // 稀疏数组，用来存储自定义数据信息
        private SparseArray<Object> mData;

        // 当前所处的布局的阶段
        int mLayoutStep = STEP_START;

        // ... 其他
    }
```

### onMeasure()

```java
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (mLayout == null) {
            // LM 为空，使用默认的测量结果（类似于普通的 View）
            defaultOnMeasure(widthSpec, heightSpec);
            return;
        }
        // isAutoMeasureEnabled() 返回 true 表示使用 RV 自动测量的结果
        if (mLayout.isAutoMeasureEnabled()) {
            // ...

            // 第一步：更新 adpater，决定执行的动画，保存控件信息，预布局并保存其信息
            if (mState.mLayoutStep == State.STEP_START) {
                dispatchLayoutStep1();
            }

            // ...
            // 第二步：根据最终的状态进行布局的地方
            dispatchLayoutStep2();

            // ...
        } else {
            if (mHasFixedSize) {
                mLayout.onMeasure(mRecycler, mState, widthSpec, heightSpec);
                return; 
            }
            // ...
            mLayout.onMeasure(mRecycler, mState, widthSpec, heightSpec);
            mState.mInPreLayout = false; // clear
        }
    }
```

`startInterceptRequestLayout()` 和 `stopInterceptRequestLayout(false)` 方法的调用应该成对存在。它们分别通过对一个整数进行递增和递减。然后在 RecyclerView 的 `requestLayout()` 方法中，通过判断该整数值是否为 0 来防止多次调用 RecyclerView 的 `requestLayout()` 方法。通常这两个方法的调用应该是成对存在的。第一方法应该在 RV 的 `requestLayout()` 被调用之前调用，第二个应该在之后被调用。

```java
    private void dispatchLayoutStep1() {
        mState.mIsMeasuring = false;
        mViewInfoStore.clear();
        // 进行 adapter 的更新，设置 animation
        processAdapterUpdatesAndSetAnimationFlags();
        saveFocusInfo();
        mState.mTrackOldChangeHolders = mState.mRunSimpleAnimations && mItemsChanged;
        mItemsAddedOrRemoved = mItemsChanged = false;
        mState.mInPreLayout = mState.mRunPredictiveAnimations;
        mState.mItemCount = mAdapter.getItemCount();
        findMinMaxChildLayoutPositions(mMinMaxLayoutPositions);

        if (mState.mRunSimpleAnimations) {
            // Step 0: 找出所有没有被移除的条目，进行预布局
            int count = mChildHelper.getChildCount();
            for (int i = 0; i < count; ++i) {
                final ViewHolder holder = getChildViewHolderInt(mChildHelper.getChildAt(i));
                if (holder.shouldIgnore() || (holder.isInvalid() && !mAdapter.hasStableIds())) {
                    continue;
                }
                final ItemHolderInfo animationInfo = mItemAnimator
                        .recordPreLayoutInformation(mState, holder,
                                ItemAnimator.buildAdapterChangeFlagsForAnimations(holder),
                                holder.getUnmodifiedPayloads());
                mViewInfoStore.addToPreLayout(holder, animationInfo);
                if (mState.mTrackOldChangeHolders && holder.isUpdated() && !holder.isRemoved()
                        && !holder.shouldIgnore() && !holder.isInvalid()) {
                    long key = getChangedHolderKey(holder);
                    mViewInfoStore.addToOldChangeHolders(key, holder);
                }
            }
        }
        if (mState.mRunPredictiveAnimations) {
            saveOldPositions(); // 保存旧的位置信息，以便 LM 可以进行映射
            final boolean didStructureChange = mState.mStructureChanged;
            mState.mStructureChanged = false;
            // temporarily disable flag because we are asking for previous layout
            mLayout.onLayoutChildren(mRecycler, mState);
            mState.mStructureChanged = didStructureChange;

            for (int i = 0; i < mChildHelper.getChildCount(); ++i) {
                final View child = mChildHelper.getChildAt(i);
                final ViewHolder viewHolder = getChildViewHolderInt(child);
                if (viewHolder.shouldIgnore()) {
                    continue;
                }
                if (!mViewInfoStore.isInPreLayout(viewHolder)) {
                    int flags = ItemAnimator.buildAdapterChangeFlagsForAnimations(viewHolder);
                    boolean wasHidden = viewHolder
                            .hasAnyOfTheFlags(ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
                    if (!wasHidden) {
                        flags |= ItemAnimator.FLAG_APPEARED_IN_PRE_LAYOUT;
                    }
                    final ItemHolderInfo animationInfo = mItemAnimator.recordPreLayoutInformation(
                            mState, viewHolder, flags, viewHolder.getUnmodifiedPayloads());
                    if (wasHidden) {
                        recordAnimationInfoIfBouncedHiddenView(viewHolder, animationInfo);
                    } else {
                        mViewInfoStore.addToAppearedInPreLayoutHolders(viewHolder, animationInfo);
                    }
                }
            }
            // 清除旧的位置信息
            clearOldPositions();
        } else {
            clearOldPositions();
        }
        mState.mLayoutStep = State.STEP_LAYOUT;
    }
```

布局 2：

```java
    private void dispatchLayoutStep2() {
        mAdapterHelper.consumeUpdatesInOnePass();
        mState.mItemCount = mAdapter.getItemCount();
        mState.mDeletedInvisibleItemCountSincePreviousLayout = 0;

        // Step 2: Run layout
        mState.mInPreLayout = false;
        mLayout.onLayoutChildren(mRecycler, mState);

        mState.mStructureChanged = false;
        mPendingSavedState = null;

        // onLayoutChildren may have caused client code to disable item animations; re-check
        mState.mRunSimpleAnimations = mState.mRunSimpleAnimations && mItemAnimator != null;
        mState.mLayoutStep = State.STEP_ANIMATIONS;
    }
```

LinearLayoutManager#onLayoutChildren()

```java
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        // ...

        final View focused = getFocusedChild();
        if (!mAnchorInfo.mValid || mPendingScrollPosition != RecyclerView.NO_POSITION
                || mPendingSavedState != null) {
            mAnchorInfo.reset();
            mAnchorInfo.mLayoutFromEnd = mShouldReverseLayout ^ mStackFromEnd;
            // calculate anchor position and coordinate
            updateAnchorInfoForLayout(recycler, state, mAnchorInfo);
            mAnchorInfo.mValid = true;
        } else if (focused != null && (mOrientationHelper.getDecoratedStart(focused)
                        >= mOrientationHelper.getEndAfterPadding()
                || mOrientationHelper.getDecoratedEnd(focused)
                <= mOrientationHelper.getStartAfterPadding())) {
            mAnchorInfo.assignFromViewAndKeepVisibleRect(focused, getPosition(focused));
        }

        // LLM may decide to layout items for "extra" pixels to account for scrolling target,
        // caching or predictive animations.
        int extraForStart;
        int extraForEnd;
        final int extra = getExtraLayoutSpace(state);
        // If the previous scroll delta was less than zero, the extra space should be laid out
        // at the start. Otherwise, it should be at the end.
        if (mLayoutState.mLastScrollDelta >= 0) {
            extraForEnd = extra;
            extraForStart = 0;
        } else {
            extraForStart = extra;
            extraForEnd = 0;
        }
        extraForStart += mOrientationHelper.getStartAfterPadding();
        extraForEnd += mOrientationHelper.getEndPadding();
        if (state.isPreLayout() && mPendingScrollPosition != RecyclerView.NO_POSITION
                && mPendingScrollPositionOffset != INVALID_OFFSET) {
            // if the child is visible and we are going to move it around, we should layout
            // extra items in the opposite direction to make sure new items animate nicely
            // instead of just fading in
            final View existing = findViewByPosition(mPendingScrollPosition);
            if (existing != null) {
                final int current;
                final int upcomingOffset;
                if (mShouldReverseLayout) {
                    current = mOrientationHelper.getEndAfterPadding()
                            - mOrientationHelper.getDecoratedEnd(existing);
                    upcomingOffset = current - mPendingScrollPositionOffset;
                } else {
                    current = mOrientationHelper.getDecoratedStart(existing)
                            - mOrientationHelper.getStartAfterPadding();
                    upcomingOffset = mPendingScrollPositionOffset - current;
                }
                if (upcomingOffset > 0) {
                    extraForStart += upcomingOffset;
                } else {
                    extraForEnd -= upcomingOffset;
                }
            }
        }
        int startOffset;
        int endOffset;
        final int firstLayoutDirection;
        if (mAnchorInfo.mLayoutFromEnd) {
            firstLayoutDirection = mShouldReverseLayout ? LayoutState.ITEM_DIRECTION_TAIL
                    : LayoutState.ITEM_DIRECTION_HEAD;
        } else {
            firstLayoutDirection = mShouldReverseLayout ? LayoutState.ITEM_DIRECTION_HEAD
                    : LayoutState.ITEM_DIRECTION_TAIL;
        }

        onAnchorReady(recycler, state, mAnchorInfo, firstLayoutDirection);
        detachAndScrapAttachedViews(recycler);
        mLayoutState.mInfinite = resolveIsInfinite();
        mLayoutState.mIsPreLayout = state.isPreLayout();
        if (mAnchorInfo.mLayoutFromEnd) {
            // fill towards start
            updateLayoutStateToFillStart(mAnchorInfo);
            mLayoutState.mExtra = extraForStart;
            fill(recycler, mLayoutState, state, false);
            startOffset = mLayoutState.mOffset;
            final int firstElement = mLayoutState.mCurrentPosition;
            if (mLayoutState.mAvailable > 0) {
                extraForEnd += mLayoutState.mAvailable;
            }
            // fill towards end
            updateLayoutStateToFillEnd(mAnchorInfo);
            mLayoutState.mExtra = extraForEnd;
            mLayoutState.mCurrentPosition += mLayoutState.mItemDirection;
            fill(recycler, mLayoutState, state, false);
            endOffset = mLayoutState.mOffset;

            if (mLayoutState.mAvailable > 0) {
                // end could not consume all. add more items towards start
                extraForStart = mLayoutState.mAvailable;
                updateLayoutStateToFillStart(firstElement, startOffset);
                mLayoutState.mExtra = extraForStart;
                fill(recycler, mLayoutState, state, false);
                startOffset = mLayoutState.mOffset;
            }
        } else {
            // fill towards end
            updateLayoutStateToFillEnd(mAnchorInfo);
            mLayoutState.mExtra = extraForEnd;
            fill(recycler, mLayoutState, state, false);
            endOffset = mLayoutState.mOffset;
            final int lastElement = mLayoutState.mCurrentPosition;
            if (mLayoutState.mAvailable > 0) {
                extraForStart += mLayoutState.mAvailable;
            }
            // fill towards start
            updateLayoutStateToFillStart(mAnchorInfo);
            mLayoutState.mExtra = extraForStart;
            mLayoutState.mCurrentPosition += mLayoutState.mItemDirection;
            fill(recycler, mLayoutState, state, false);
            startOffset = mLayoutState.mOffset;

            if (mLayoutState.mAvailable > 0) {
                extraForEnd = mLayoutState.mAvailable;
                // start could not consume all it should. add more items towards end
                updateLayoutStateToFillEnd(lastElement, endOffset);
                mLayoutState.mExtra = extraForEnd;
                fill(recycler, mLayoutState, state, false);
                endOffset = mLayoutState.mOffset;
            }
        }

        // changes may cause gaps on the UI, try to fix them.
        // TODO we can probably avoid this if neither stackFromEnd/reverseLayout/RTL values have
        // changed
        if (getChildCount() > 0) {
            // because layout from end may be changed by scroll to position
            // we re-calculate it.
            // find which side we should check for gaps.
            if (mShouldReverseLayout ^ mStackFromEnd) {
                int fixOffset = fixLayoutEndGap(endOffset, recycler, state, true);
                startOffset += fixOffset;
                endOffset += fixOffset;
                fixOffset = fixLayoutStartGap(startOffset, recycler, state, false);
                startOffset += fixOffset;
                endOffset += fixOffset;
            } else {
                int fixOffset = fixLayoutStartGap(startOffset, recycler, state, true);
                startOffset += fixOffset;
                endOffset += fixOffset;
                fixOffset = fixLayoutEndGap(endOffset, recycler, state, false);
                startOffset += fixOffset;
                endOffset += fixOffset;
            }
        }
        layoutForPredictiveAnimations(recycler, state, startOffset, endOffset);
        if (!state.isPreLayout()) {
            mOrientationHelper.onLayoutComplete();
        } else {
            mAnchorInfo.reset();
        }
        mLastStackFromEnd = mStackFromEnd;
    }
```

布局 3：

```java
    private void dispatchLayoutStep3() {
        mState.mLayoutStep = State.STEP_START;
        if (mState.mRunSimpleAnimations) {
            // 步骤 3：执行更新动画
            for (int i = mChildHelper.getChildCount() - 1; i >= 0; i--) {
                ViewHolder holder = getChildViewHolderInt(mChildHelper.getChildAt(i));
                if (holder.shouldIgnore()) {
                    continue;
                }
                long key = getChangedHolderKey(holder);
                final ItemHolderInfo animationInfo = mItemAnimator
                        .recordPostLayoutInformation(mState, holder);
                ViewHolder oldChangeViewHolder = mViewInfoStore.getFromOldChangeHolders(key);
                if (oldChangeViewHolder != null && !oldChangeViewHolder.shouldIgnore()) {
                    // 执行动画
                    final boolean oldDisappearing = mViewInfoStore.isDisappearing(oldChangeViewHolder);
                    final boolean newDisappearing = mViewInfoStore.isDisappearing(holder);
                    if (oldDisappearing && oldChangeViewHolder == holder) {
                        // run disappear animation instead of change
                        mViewInfoStore.addToPostLayout(holder, animationInfo);
                    } else {
                        final ItemHolderInfo preInfo = mViewInfoStore.popFromPreLayout(
                                oldChangeViewHolder);
                        // we add and remove so that any post info is merged.
                        mViewInfoStore.addToPostLayout(holder, animationInfo);
                        ItemHolderInfo postInfo = mViewInfoStore.popFromPostLayout(holder);
                        if (preInfo == null) {
                            handleMissingPreInfoForChangeError(key, holder, oldChangeViewHolder);
                        } else {
                            animateChange(oldChangeViewHolder, holder, preInfo, postInfo,
                                    oldDisappearing, newDisappearing);
                        }
                    }
                } else {
                    mViewInfoStore.addToPostLayout(holder, animationInfo);
                }
            }

            // 步骤 4: 处理控件信息，触发动画
            mViewInfoStore.process(mViewInfoProcessCallback);
        }

        mLayout.removeAndRecycleScrapInt(mRecycler);
        mState.mPreviousLayoutItemCount = mState.mItemCount;
        mDataSetHasChangedAfterLayout = false;
        mDispatchItemsChangedEvent = false;
        mState.mRunSimpleAnimations = false;

        mState.mRunPredictiveAnimations = false;
        mLayout.mRequestedSimpleAnimations = false;
        if (mRecycler.mChangedScrap != null) {
            mRecycler.mChangedScrap.clear();
        }
        if (mLayout.mPrefetchMaxObservedInInitialPrefetch) {
            mLayout.mPrefetchMaxCountObserved = 0;
            mLayout.mPrefetchMaxObservedInInitialPrefetch = false;
            mRecycler.updateViewCacheSize();
        }

        mLayout.onLayoutCompleted(mState);
        mViewInfoStore.clear();
        if (didChildRangeChange(mMinMaxLayoutPositions[0], mMinMaxLayoutPositions[1])) {
            dispatchOnScrolled(0, 0);
        }
        recoverFocusFromState();
        resetFocusInfo();
    }
```

### onLayout()

```java
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        dispatchLayout();
        mFirstLayoutComplete = true;
    }

    void dispatchLayout() {
        mState.mIsMeasuring = false;
        if (mState.mLayoutStep == State.STEP_START) {
            dispatchLayoutStep1();
            mLayout.setExactMeasureSpecsFrom(this);
            dispatchLayoutStep2();
        } else if (mAdapterHelper.hasUpdates() || mLayout.getWidth() != getWidth()
                || mLayout.getHeight() != getHeight()) {
            mLayout.setExactMeasureSpecsFrom(this);
            dispatchLayoutStep2();
        } else {
            mLayout.setExactMeasureSpecsFrom(this);
        }
        dispatchLayoutStep3();
    }
```

### onDraw()

```java
    public void draw(Canvas c) {
        super.draw(c);

        // ItemDecoration 被调用的地方 1
        final int count = mItemDecorations.size();
        for (int i = 0; i < count; i++) {
            mItemDecorations.get(i).onDrawOver(c, this, mState);
        }

        boolean needsInvalidate = false;
        if (mLeftGlow != null && !mLeftGlow.isFinished()) {
            final int restore = c.save();
            final int padding = mClipToPadding ? getPaddingBottom() : 0;
            c.rotate(270);
            c.translate(-getHeight() + padding, 0);
            needsInvalidate = mLeftGlow != null && mLeftGlow.draw(c);
            c.restoreToCount(restore);
        }
        if (mTopGlow != null && !mTopGlow.isFinished()) {
            final int restore = c.save();
            if (mClipToPadding) {
                c.translate(getPaddingLeft(), getPaddingTop());
            }
            needsInvalidate |= mTopGlow != null && mTopGlow.draw(c);
            c.restoreToCount(restore);
        }
        if (mRightGlow != null && !mRightGlow.isFinished()) {
            final int restore = c.save();
            final int width = getWidth();
            final int padding = mClipToPadding ? getPaddingTop() : 0;
            c.rotate(90);
            c.translate(-padding, -width);
            needsInvalidate |= mRightGlow != null && mRightGlow.draw(c);
            c.restoreToCount(restore);
        }
        if (mBottomGlow != null && !mBottomGlow.isFinished()) {
            final int restore = c.save();
            c.rotate(180);
            if (mClipToPadding) {
                c.translate(-getWidth() + getPaddingRight(), -getHeight() + getPaddingBottom());
            } else {
                c.translate(-getWidth(), -getHeight());
            }
            needsInvalidate |= mBottomGlow != null && mBottomGlow.draw(c);
            c.restoreToCount(restore);
        }

        if (!needsInvalidate && mItemAnimator != null && mItemDecorations.size() > 0
                && mItemAnimator.isRunning()) {
            needsInvalidate = true;
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }
```


```java
    public void onDraw(Canvas c) {
        super.onDraw(c);

        // ItemDecoration 被调用的地方 2
        final int count = mItemDecorations.size();
        for (int i = 0; i < count; i++) {
            mItemDecorations.get(i).onDraw(c, this, mState);
        }
    }
```

### 再认识 ViewHolder 

定义了一些 FLAG 常量。然后内部定义了整型的 flag 字段，包含了当前的 flag 信息：

```java
    public abstract static class ViewHolder {

        static final int FLAG_BOUND = 1 << 0;

        // ... 其他 FLAG

        int mFlags; // 当前的 flag 信息

        // ... 其他方法和字段
    }
```

### ItemAnimator

这个类定义了当 item 发生变化的时候应用到 item 上面的动画。

它还有一个内部类 ItemHolderInfo，它是印个包含了 item 的边界信息的数据结构，用在 item 的动画的计算中。

RV 中默认使用 DefaultItemAnimator 的实现。

DefaultItemAnimator 中提供了一些列表。它们包含了要作动画的 ViewHolder 信息：

```java
    private ArrayList<RecyclerView.ViewHolder> mPendingRemovals = new ArrayList<>();
    private ArrayList<RecyclerView.ViewHolder> mPendingAdditions = new ArrayList<>();
    private ArrayList<MoveInfo> mPendingMoves = new ArrayList<>();
    private ArrayList<ChangeInfo> mPendingChanges = new ArrayList<>();
```

RV 中的 `animateAppearance()` `animateDisappearance()` 和 `animateChange()` 三个方法用来向 DefaultItemAnimator 中添加 ViewHolder 信息。然后，当需要进行动画的时候，执行下下面的 Runnable：

```java
    private Runnable mItemAnimatorRunner = new Runnable() {
        @Override
        public void run() {
            if (mItemAnimator != null) {
                mItemAnimator.runPendingAnimations();
            }
            mPostedAnimatorRunner = false;
        }
    };
```

然后，DefaultItemAnimator 就会从上述的 ArrayList 中取出 ViewHolder 并对它们执行动画。

那么问题来了，上面的 RV 中的三个方法在什么时候调用呢？

```java
    private final ViewInfoStore.ProcessCallback mViewInfoProcessCallback =
            new ViewInfoStore.ProcessCallback() {
                @Override
                public void processDisappeared(ViewHolder viewHolder, ItemHolderInfo info, ItemHolderInfo postInfo) {
                    mRecycler.unscrapView(viewHolder);
                    // 被调用 1
                    animateDisappearance(viewHolder, info, postInfo);
                }
                @Override
                public void processAppeared(ViewHolder viewHolder, ItemHolderInfo preInfo, ItemHolderInfo info) {
                    // 被调用 2 
                    animateAppearance(viewHolder, preInfo, info);
                }

                @Override
                public void processPersistent(ViewHolder viewHolder, ItemHolderInfo preInfo, ItemHolderInfo postInfo) {
                    viewHolder.setIsRecyclable(false);
                    if (mDataSetHasChangedAfterLayout) {
                        if (mItemAnimator.animateChange(viewHolder, viewHolder, preInfo, postInfo)) {
                            postAnimationRunner();
                        }
                        // 被调用 3
                    } else if (mItemAnimator.animatePersistence(viewHolder, preInfo, postInfo)) {
                        postAnimationRunner();
                    }
                }

                @Override
                public void unused(ViewHolder viewHolder) {
                    mLayout.removeAndRecycleView(viewHolder.itemView, mRecycler);
                }
            };
```

然后 `mViewInfoProcessCallback` 在 `dispatchLayoutStep3()` 中被传递到 ViewHolderStore 中：

```java
mViewInfoStore.process(mViewInfoProcessCallback);
```

### ItemDecoration

`onDraw()` 和 `draw()` 两个方法，使用到的位置等

### 触摸事件

ItemTouchHelper 继承了 `RecyclerView.ItemDecoration` 并实现了 `RecyclerView.OnChildAttachStateChangeListener` 接口。

```java
    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        if (mRecyclerView == recyclerView) {
            return; // nothing to do
        }
        if (mRecyclerView != null) {
            destroyCallbacks();
        }
        mRecyclerView = recyclerView;
        if (recyclerView != null) {
            // ...
            setupCallbacks();
        }
    }

    private void setupCallbacks() {
        ViewConfiguration vc = ViewConfiguration.get(mRecyclerView.getContext());
        mSlop = vc.getScaledTouchSlop();
        mRecyclerView.addItemDecoration(this);
        mRecyclerView.addOnItemTouchListener(mOnItemTouchListener);
        mRecyclerView.addOnChildAttachStateChangeListener(this);
        startGestureDetection();
    }
````

这里的 mOnItemTouchListener 是 OnItemTouchListener 的实例，实现了该接口中的三个方法。






