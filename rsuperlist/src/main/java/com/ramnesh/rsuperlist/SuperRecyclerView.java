package com.ramnesh.rsuperlist;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.ColorRes;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.TextView;


@SuppressWarnings("ALL")
public class SuperRecyclerView extends FrameLayout {

    protected int ITEM_LEFT_TO_LOAD_MORE = 10;

    protected RecyclerView mRecycler;
    protected ViewStub     mProgress;
    protected ViewStub     mMoreProgress;
    protected ViewStub     mEmpty;
    protected View         mProgressView;
    protected View         mMoreProgressView;
    protected View         mEmptyView;

    protected boolean mClipToPadding;
    protected int     mPadding;
    protected int     mPaddingTop;
    protected int     mPaddingBottom;
    protected int     mPaddingLeft;
    protected int     mPaddingRight;

    protected int     mEmptyId;


    protected LAYOUT_MANAGER_TYPE layoutManagerType;

    protected RecyclerView.OnScrollListener mInternalOnScrollListener;
    private   RecyclerView.OnScrollListener mSwipeDismissScrollListener;
    protected RecyclerView.OnScrollListener mExternalOnScrollListener;

    protected OnMoreListener     mOnMoreListener;
    protected boolean            isLoadingMore;
    protected SwipeRefreshLayout mPtrLayout;

    protected int mSuperRecyclerViewMainLayout;
    private   int mProgressId;

    private int[] lastScrollPositions;

    public SwipeRefreshLayout getSwipeToRefresh() {
        return mPtrLayout;
    }

    public RecyclerView getRecyclerView() {
        return mRecycler;
    }

    public SuperRecyclerView(Context context) {
        super(context);
        initView();
    }

    public SuperRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttrs(attrs);
        initView();
    }

    public SuperRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initAttrs(attrs);
        initView();
    }

    protected void initAttrs(AttributeSet attrs) {
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.rsuperlist);
        try {
            mSuperRecyclerViewMainLayout = a.getResourceId(R.styleable.rsuperlist_mainLayoutId, R.layout.layout_progress_recyclerview);
            mClipToPadding = a.getBoolean(R.styleable.rsuperlist_recyclerClipToPadding, false);
            mPadding = (int) a.getDimension(R.styleable.rsuperlist_recyclerPadding, -1.0f);
            mPaddingTop = (int) a.getDimension(R.styleable.rsuperlist_recyclerPaddingTop, 0.0f);
            mPaddingBottom = (int) a.getDimension(R.styleable.rsuperlist_recyclerPaddingBottom, 0.0f);
            mPaddingLeft = (int) a.getDimension(R.styleable.rsuperlist_recyclerPaddingLeft, 0.0f);
            mPaddingRight = (int) a.getDimension(R.styleable.rsuperlist_recyclerPaddingRight, 0.0f);
            mEmptyId = R.layout.emptyview;

            mProgressId = a.getResourceId(R.styleable.rsuperlist_layout_progress, R.layout.layout_progress);
        } finally {
            a.recycle();
        }
    }

    private void initView() {
        if (isInEditMode()) {
            return;
        }
        View v = LayoutInflater.from(getContext()).inflate(mSuperRecyclerViewMainLayout, this);
        mPtrLayout = (SwipeRefreshLayout) v.findViewById(R.id.ptr_layout);
        mPtrLayout.setEnabled(false);

        mProgress = (ViewStub) v.findViewById(android.R.id.progress);

        mProgress.setLayoutResource(mProgressId);
        mProgressView = mProgress.inflate();

        mMoreProgress = (ViewStub) v.findViewById(R.id.more_progress);
        mMoreProgress.setLayoutResource(mProgressId);
        if (mProgressId != 0)
            mMoreProgressView = mMoreProgress.inflate();
        mMoreProgress.setVisibility(View.GONE);

        mEmpty = (ViewStub) v.findViewById(R.id.empty);
        mEmpty.setLayoutResource(mEmptyId);
        if (mEmptyId != 0)
            mEmptyView = mEmpty.inflate();
        mEmpty.setVisibility(View.GONE);

        initRecyclerView(v);
    }

    /**
     * Implement this method to customize the AbsListView
     */
    protected void initRecyclerView(View view) {
        View recyclerView = view.findViewById(android.R.id.list);

        if (recyclerView instanceof RecyclerView)
            mRecycler = (RecyclerView) recyclerView;
        else
            throw new IllegalArgumentException("SuperRecyclerView works with a RecyclerView!");


        if (mRecycler != null) {
            mRecycler.setClipToPadding(mClipToPadding);
            mInternalOnScrollListener = new RecyclerView.OnScrollListener() {

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);

                    if(recyclerView.getAdapter().getItemCount()>0)
                    processOnMore();

                    if (mExternalOnScrollListener != null)
                        mExternalOnScrollListener.onScrolled(recyclerView, dx, dy);
                    if (mSwipeDismissScrollListener != null)
                        mSwipeDismissScrollListener.onScrolled(recyclerView, dx, dy);
                }

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    if (mExternalOnScrollListener != null)
                        mExternalOnScrollListener.onScrollStateChanged(recyclerView, newState);
                    if (mSwipeDismissScrollListener != null)
                        mSwipeDismissScrollListener.onScrollStateChanged(recyclerView, newState);
                }
            };
            mRecycler.addOnScrollListener(mInternalOnScrollListener);

            if (mPadding != -1.0f) {
                mRecycler.setPadding(mPadding, mPadding, mPadding, mPadding);
            } else {
                mRecycler.setPadding(mPaddingLeft, mPaddingTop, mPaddingRight, mPaddingBottom);
            }

        }
    }

    private void processOnMore() {
        RecyclerView.LayoutManager layoutManager = mRecycler.getLayoutManager();
        int lastVisibleItemPosition = getLastVisibleItemPosition(layoutManager);
        int visibleItemCount = layoutManager.getChildCount();
        int totalItemCount = layoutManager.getItemCount();

        if (((totalItemCount - lastVisibleItemPosition) <= ITEM_LEFT_TO_LOAD_MORE ||
             (totalItemCount - lastVisibleItemPosition) == 0 && totalItemCount > visibleItemCount)
            && !isLoadingMore) {

            isLoadingMore = true;
            if (mOnMoreListener != null) {
                mMoreProgress.setVisibility(View.VISIBLE);
                mOnMoreListener.onMoreAsked(mRecycler.getAdapter().getItemCount(), ITEM_LEFT_TO_LOAD_MORE, lastVisibleItemPosition);
            }
        }
    }

    private int getLastVisibleItemPosition(RecyclerView.LayoutManager layoutManager) {
        int lastVisibleItemPosition = -1;
        if (layoutManagerType == null) {
            if (layoutManager instanceof LinearLayoutManager) {
                layoutManagerType = LAYOUT_MANAGER_TYPE.LINEAR;
            } else if (layoutManager instanceof GridLayoutManager) {
                layoutManagerType = LAYOUT_MANAGER_TYPE.GRID;
            } else if (layoutManager instanceof StaggeredGridLayoutManager) {
                layoutManagerType = LAYOUT_MANAGER_TYPE.STAGGERED_GRID;
            } else {
                throw new RuntimeException("Unsupported LayoutManager used. Valid ones are LinearLayoutManager, GridLayoutManager and StaggeredGridLayoutManager");
            }
        }

        switch (layoutManagerType) {
            case LINEAR:
                lastVisibleItemPosition = ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition();
                break;
            case GRID:
                lastVisibleItemPosition = ((GridLayoutManager) layoutManager).findLastVisibleItemPosition();
                break;
            case STAGGERED_GRID:
                StaggeredGridLayoutManager staggeredGridLayoutManager = (StaggeredGridLayoutManager) layoutManager;
                if (lastScrollPositions == null)
                    lastScrollPositions = new int[staggeredGridLayoutManager.getSpanCount()];

                staggeredGridLayoutManager.findLastVisibleItemPositions(lastScrollPositions);
                lastVisibleItemPosition = findMax(lastScrollPositions);
                break;
        }
        return lastVisibleItemPosition;
    }

    private int findMax(int[] lastPositions) {
        int max = Integer.MIN_VALUE;
        for (int value : lastPositions) {
            if (value > max)
                max = value;
        }
        return max;
    }

    /**
     * @param adapter                       The new adapter to set, or null to set no adapter
     * @param compatibleWithPrevious        Should be set to true if new adapter uses the same {@android.support.v7.widget.RecyclerView.ViewHolder} as previous one
     * @param removeAndRecycleExistingViews If set to true, RecyclerView will recycle all existing Views. If adapters have stable ids and/or you want to animate the disappearing views, you may prefer to set this to false
     */
    private void setAdapterInternal(RecyclerView.Adapter adapter, boolean compatibleWithPrevious,
                                    boolean removeAndRecycleExistingViews) {
        if (compatibleWithPrevious)
            mRecycler.swapAdapter(adapter, removeAndRecycleExistingViews);
        else
            mRecycler.setAdapter(adapter);

        mProgress.setVisibility(View.GONE);
        mRecycler.setVisibility(View.VISIBLE);
        mPtrLayout.setRefreshing(false);
        if (null != adapter)
            adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onItemRangeChanged(int positionStart, int itemCount) {
                    super.onItemRangeChanged(positionStart, itemCount);
                    update();
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    super.onItemRangeInserted(positionStart, itemCount);
                    update();
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    super.onItemRangeRemoved(positionStart, itemCount);
                    update();
                }

                @Override
                public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                    super.onItemRangeMoved(fromPosition, toPosition, itemCount);
                    update();
                }

                @Override
                public void onChanged() {
                    super.onChanged();
                    update();
                }

                private void update() {
                    mProgress.setVisibility(View.GONE);
                    mMoreProgress.setVisibility(View.GONE);
                    isLoadingMore = false;
                    mPtrLayout.setRefreshing(false);
                    if (mRecycler.getAdapter().getItemCount() == 0 && mEmptyId != 0) {

                        mEmpty.setVisibility(View.VISIBLE);
                    } else if (mEmptyId != 0) {
                        mEmpty.setVisibility(View.GONE);
                    }
                }
            });
        if(null!=adapter&&adapter.getItemCount()>0&&mEmptyId!=0)
        {
            mEmpty.setVisibility(GONE);
        }
        else{

            mEmpty.setVisibility(VISIBLE);
        }
       /* mEmpty.setVisibility(null != adapter && adapter.getItemCount() > 0 && mEmptyId != 0
                             ? View.GONE
                             : View.VISIBLE);*/
    }

    /**
     * Set the layout manager to the recycler
     *
     * @param manager
     */
    public void setLayoutManager(RecyclerView.LayoutManager manager) {
        mRecycler.setLayoutManager(manager);
    }

    /**
     * Add layout animator*/
    public void setItemAnimator(RecyclerView.ItemAnimator animator)
    {
        if(mRecycler!=null)
        mRecycler.setItemAnimator(animator);
    }

    /**
     * Set the adapter to the recycler
     * Automatically hide the progressbar
     * Set the refresh to false
     * If adapter is empty, then the emptyview is shown
     *
     * @param adapter
     */
    public void setAdapter(RecyclerView.Adapter adapter) {
        setAdapterInternal(adapter, false, true);
    }

    /**
     * @param adapter                       The new adapter to , or null to set no adapter.
     * @param removeAndRecycleExistingViews If set to true, RecyclerView will recycle all existing Views. If adapters have stable ids and/or you want to animate the disappearing views, you may prefer to set this to false.
     */
    public void swapAdapter(RecyclerView.Adapter adapter, boolean removeAndRecycleExistingViews) {
        setAdapterInternal(adapter, true, removeAndRecycleExistingViews);
    }


    /**
     * Remove the adapter from the recycler
     */
    public void clear() {
        mRecycler.setAdapter(null);
    }

    /**
     * Show the progressbar
     */
    public void showProgress() {
        hideRecycler();
        if (mEmptyId != 0) mEmpty.setVisibility(View.INVISIBLE);
        mProgress.setVisibility(View.VISIBLE);
    }

    /**
     * Hide the progressbar and show the recycler
     */
    public void showRecycler() {
        hideProgress();
        mRecycler.setVisibility(View.VISIBLE);
    }

    public void showMoreProgress() {
        mMoreProgress.setVisibility(View.VISIBLE);
    }

    public void hideMoreProgress() {
        mMoreProgress.setVisibility(View.GONE);
    }

    /**
     * Set the listener when refresh is triggered and enable the SwipeRefreshLayout
     *
     * @param listener
     */
    public void setRefreshListener(SwipeRefreshLayout.OnRefreshListener listener) {
        mPtrLayout.setEnabled(true);
        mPtrLayout.setOnRefreshListener(listener);
    }

    /**
     * Set the colors for the SwipeRefreshLayout states
     *
     * @param colRes1
     * @param colRes2
     * @param colRes3
     * @param colRes4
     */
    public void setRefreshingColorResources(@ColorRes int colRes1, @ColorRes int colRes2, @ColorRes int colRes3, @ColorRes int colRes4) {
        mPtrLayout.setColorSchemeResources(colRes1, colRes2, colRes3, colRes4);
    }

    /**
     * Set the colors for the SwipeRefreshLayout states
     *
     * @param col1
     * @param col2
     * @param col3
     * @param col4
     */
    public void setRefreshingColor(int col1, int col2, int col3, int col4) {
        mPtrLayout.setColorSchemeColors(col1, col2, col3, col4);
    }

    /**
     * Hide the progressbar
     */
    public void hideProgress() {
        mProgress.setVisibility(View.GONE);
    }

    /**
     * Hide the recycler
     */
    public void hideRecycler() {
        mRecycler.setVisibility(View.GONE);
    }

    /**
     * Set the scroll listener for the recycler
     *
     * @param listener
     */
    public void setOnScrollListener(RecyclerView.OnScrollListener listener) {
        mExternalOnScrollListener = listener;
    }

    /**
     * Add the onItemTouchListener for the recycler
     *
     * @param listener
     */
    public void addOnItemTouchListener(RecyclerView.OnItemTouchListener listener) {
        mRecycler.addOnItemTouchListener(listener);
    }

    /**
     * Remove the onItemTouchListener for the recycler
     *
     * @param listener
     */
    public void removeOnItemTouchListener(RecyclerView.OnItemTouchListener listener) {
        mRecycler.removeOnItemTouchListener(listener);
    }

    /**
     * @return the recycler adapter
     */
    public RecyclerView.Adapter getAdapter() {
        return mRecycler.getAdapter();
    }

    /**
     * Sets the More listener
     *
     * @param onMoreListener
     * @param max            Number of items before loading more
     */
    public void setupMoreListener(OnMoreListener onMoreListener, int max) {
        mOnMoreListener = onMoreListener;
        ITEM_LEFT_TO_LOAD_MORE = max;
    }

    public void setOnMoreListener(OnMoreListener onMoreListener) {
        mOnMoreListener = onMoreListener;
    }

    public void setNumberBeforeMoreIsCalled(int max) {
        ITEM_LEFT_TO_LOAD_MORE = max;
    }

    public boolean isLoadingMore() {
        return isLoadingMore;
    }

    /**
     * Enable/Disable the More event
     *
     * @param isLoadingMore
     */
    public void setLoadingMore(boolean isLoadingMore) {
        this.isLoadingMore = isLoadingMore;
    }

    /**
     * Remove the moreListener
     */
    public void removeMoreListener() {
        mOnMoreListener = null;
    }


    public void setOnTouchListener(OnTouchListener listener) {
        mRecycler.setOnTouchListener(listener);
    }

    public void addItemDecoration(RecyclerView.ItemDecoration itemDecoration) {
        mRecycler.addItemDecoration(itemDecoration);
    }

    public void addItemDecoration(RecyclerView.ItemDecoration itemDecoration, int index) {
        mRecycler.addItemDecoration(itemDecoration, index);
    }

    public void removeItemDecoration(RecyclerView.ItemDecoration itemDecoration) {
        mRecycler.removeItemDecoration(itemDecoration);
    }

    /**
     * @return inflated progress view or null
     */
    public View getProgressView() {
        return mProgressView;
    }

    /**
     * @return inflated more progress view or null
     */
    public View getMoreProgressView() {
        return mMoreProgressView;
    }

    /**
     * @return inflated empty view or null
     */
    public View getEmptyView() {
        return mEmptyView;
    }

    /**
     *@param resId the drawable resource id
     *@param text The text which is showing
     */
    public void changeEmptyView(int resId,String text)
    {
        if(mEmptyId!=0)
        {
            TextView tv=(TextView)mEmptyView.findViewById(R.id.empty_text);
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(0,resId,0,0);
            tv.setText(text);
        }
    }
    /**
     *@param resId the drawable resource id
     *@param id The text which is showing
     */
    public void changeEmptyView(int resId,int id)
    {
        if(mEmptyId!=0)
        {
            TextView tv=(TextView)mEmptyView.findViewById(R.id.empty_text);
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(0,resId,0,0);
            tv.setText(id);
        }
    }

    public void removeVisibility()
    {
        if(mEmptyView!=null)
            mEmptyView.setVisibility(GONE);
    }

    /**
     * Animate a scroll by the given amount of pixels along either axis.
     *
     * @param dx Pixels to scroll horizontally
     * @param dy Pixels to scroll vertically
     */
    public void smoothScrollBy(int dx, int dy) {
        mRecycler.smoothScrollBy(dx, dy);
    }

    public enum LAYOUT_MANAGER_TYPE {
        LINEAR,
        GRID,
        STAGGERED_GRID
    }
}