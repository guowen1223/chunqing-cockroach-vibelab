package com.unity3d.accessibility;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.accessibility.CaptioningManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.ViewParent;
import com.unity3d.player.*;

import java.util.Objects;
import java.util.concurrent.Semaphore;

class UnityAccessibilityDelegate extends UnityAccessibilityDelegateBase
{
    public static final int INVALID_ID = -1;

    // The View instance on which this class performs accessibility functions.
    private volatile View m_GameView;
    private AccessibilityManager m_AccessibilityManager;
    private AccessibilityManagerListener m_AccessibilityManagerListener;
    private CaptioningManager m_CaptioningManager;
    private CaptioningChangeListener m_CaptioningChangeListener;

    // The accessible object that currently has the "accessibility focus",
    // usually indicated by a green rectangle on the screen.
    private int m_FocusedVirtualViewId = INVALID_ID;

    // When exploring the screen by touch, the item "hovered" by the finger.
    private int m_HoveredVirtualViewId = INVALID_ID;

    private boolean m_IsScreenReaderEnabled = false;
    private float m_CurrentFontScale = 1;

    //region JNI methods
    private static native int[] getRootNodeIds();
    private static native int hitTest(float x, float y);
    private static native boolean populateNodeInfo(AccessibilityNodeInfo node, int nodeId, View parentView);
    private static native boolean isNodeInvokable(int nodeId);
    private static native boolean isNodeSlider(int nodeId);
    private static native boolean isNodeScrollable(int nodeId);
    private static native boolean isNodeDismissable(int nodeId);
    private static native void onNodeFocusChanged(int nodeId, boolean focused);
    private static native boolean onNodeInvoked(int nodeId);
    private static native boolean onNodeIncremented(int nodeId);
    private static native boolean onNodeDecremented(int nodeId);
    private static native boolean onNodeScrolled(int nodeId, int direction);
    private static native boolean onNodeDismissed(int nodeId);
    private static native void onScreenReaderStatusChanged(boolean enabled);
    private static native void onFontScaleChanged(float fontScale);
    private static native void onClosedCaptioningStatusChanged(boolean enabled);
    //endregion

    UnityAccessibilityDelegate(UnityPlayer player)
    {
        super(player);
        m_GameView = player.getSurfaceView();
    }

    // Returns m_GameView, fetching it lazily if it was null at construction time
    // (e.g. when getSurfaceView() is called before the surface is created).
    private View resolveGameView()
    {
        if (m_GameView == null)
        {
            m_GameView = m_UnityPlayer.getSurfaceView();
        }
        return m_GameView;
    }

    // Called during the initialization of the Accessibility module.
    protected static UnityAccessibilityDelegate init(UnityPlayer player)
    {
        UnityAccessibilityDelegate instance = new UnityAccessibilityDelegate(player);
        instance.setup();

        return instance;
    }

    // Called during the cleanup of the Accessibility module.
    @Override
    protected void cleanup()
    {
        super.cleanup();
        if (m_AccessibilityManagerListener != null)
        {
            m_AccessibilityManagerListener.cleanup();
        }

        if (m_CaptioningChangeListener != null)
        {
            m_CaptioningChangeListener.cleanup();
        }

    }

    @Override
    protected void setup()
    {
        m_AccessibilityManager = (AccessibilityManager)(m_UnityPlayer.getContext()).getSystemService(Context.ACCESSIBILITY_SERVICE);
        m_CaptioningManager = (CaptioningManager)(m_UnityPlayer.getContext()).getSystemService(Context.CAPTIONING_SERVICE);

        if (m_AccessibilityManager != null || m_CaptioningManager != null)
        {
            // We need to run the initialization on the main UI thread, since it
            // acts on native UI.
            final Semaphore syncState = new Semaphore(0);

            invokeOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        if (m_AccessibilityManager != null)
                        {
                            m_AccessibilityManagerListener = new AccessibilityManagerListener();
                        }

                        if (m_CaptioningManager != null)
                        {
                            m_CaptioningChangeListener = new CaptioningChangeListener();
                        }
                    }
                    finally
                    {
                        syncState.release();
                    }
                }
            });

            try
            {
                syncState.acquire();
            }
            catch (InterruptedException e) {}
        }

        m_CurrentFontScale = m_UnityPlayer.getContext().getResources().getConfiguration().fontScale;

        super.setup();
    }

    public void onConfigurationChanged(Configuration newConfig)
    {
        if (newConfig.fontScale != m_CurrentFontScale)
        {
            m_CurrentFontScale = newConfig.fontScale;

            invokeOnMainThread(new Runnable() {
                @Override
                public void run() {
                    onFontScaleChanged(newConfig.fontScale);
                }
            });
        }
    }

    private void setHoveredVirtualViewId(int virtualViewId)
    {
        if (m_HoveredVirtualViewId == virtualViewId)
        {
            return;
        }

        final int previousVirtualViewId = m_HoveredVirtualViewId;
        m_HoveredVirtualViewId = virtualViewId;

        if (virtualViewId != INVALID_ID)
        {
            sendEventForVirtualViewId(virtualViewId, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        }

        if (previousVirtualViewId != INVALID_ID)
        {
            sendEventForVirtualViewId(previousVirtualViewId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
        }
    }

    private AccessibilityEvent getEventForVirtualViewId(int virtualViewId, int eventType)
    {
        var view = resolveGameView();
        if (view == null)
        {
            return null;
        }

        final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setEnabled(true);
        event.setSource(view, virtualViewId);

        return event;
    }

    protected boolean sendEventForVirtualViewIdFromNative(int virtualViewId, int eventType)
    {
        invokeOnUiThread(new Runnable () {
            @Override
            public void run()
            {
                sendEventForVirtualViewId(virtualViewId, eventType);
            }
        });

        return true;
    }

    protected boolean sendEventForVirtualViewId(int virtualViewId, int eventType)
    {
        if (!m_AccessibilityManager.isEnabled())
        {
            return false;
        }

        var view = resolveGameView();
        if (view == null)
        {
            return false;
        }

        final AccessibilityEvent event = getEventForVirtualViewId(virtualViewId, eventType);
        if (event == null)
        {
            return false;
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        {
            // Set a SUBTREE change to speedup the change.
            event.setContentChangeTypes(AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
        }

        if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
        {
            // Only handle the TYPE_VIEW_ACCESSIBILITY_FOCUSED event if it's
            // placing focus on a different view than the one previously
            // focused.
            if (m_FocusedVirtualViewId == virtualViewId)
            {
                return false;
            }

            m_FocusedVirtualViewId = virtualViewId;

            // Invalidate the view to let the screen reader redraw its focus
            // indicator.
            view.invalidate();

            // Notify the Unity thread of the focus change.
            invokeOnMainThread(new Runnable() {
                @Override
                public void run() {
                    onNodeFocusChanged(virtualViewId, true);
                }
            });
        }

        if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
        {
            // Since we're managing focus at the parent level, we are likely to
            // receive an ACTION_ACCESSIBILITY_FOCUS before an
            // ACTION_CLEAR_ACCESSIBILITY_FOCUS. We'll give the benefit of the
            // doubt to the framework and always handle
            // TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED.
            if (m_FocusedVirtualViewId == virtualViewId)
            {
                m_FocusedVirtualViewId = AccessibilityNodeProvider.HOST_VIEW_ID;
            }

            // Invalidate the view to let the screen reader redraw its focus
            // indicator.
            view.invalidate();

            // Notify the Unity thread of the focus change.
            invokeOnMainThread(new Runnable() {
                @Override
                public void run() {
                    onNodeFocusChanged(virtualViewId, false);
                }
            });
        }

        return sendAccessibilityEvent(event);
    }

    protected boolean sendAnnouncementForVirtualViewId(int virtualViewId, String announcement)
    {
        final AccessibilityEvent event = getEventForVirtualViewId(virtualViewId,
            AccessibilityEvent.TYPE_ANNOUNCEMENT);
        if (event == null)
        {
            return false;
        }

        event.getText().add(announcement);

        return sendAccessibilityEvent(event);
    }

    protected int getFocusedNodeId()
    {
        return m_FocusedVirtualViewId;
    }

    protected boolean sendAccessibilityEvent(AccessibilityEvent event)
    {
        if (event == null)
        {
            return false;
        }

        var view = resolveGameView();
        if (view == null)
        {
            return false;
        }

        final ViewGroup group = (ViewGroup) view.getParent();

        if (group == null)
        {
            return false;
        }

        return group.requestSendAccessibilityEvent(view, event);
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider(View host)
    {
        return mAccessibilityNodeProvider;
    }

    private AccessibilityNodeProvider mAccessibilityNodeProvider = new AccessibilityNodeProvider()
    {
        @Override
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId)
        {
            if (virtualViewId == HOST_VIEW_ID)
            {
                return getRootNodeInfo();
            }

            return getVirtualNodeInfo(virtualViewId);
        }

        private AccessibilityNodeInfo getRootNodeInfo()
        {
            var view = resolveGameView();
            if (view == null)
            {
                return null;
            }

            AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(view);

            // Set up the parent view if applicable.
            final ViewParent parent = view.getParent();

            if (parent instanceof View)
            {
                node.setParent((View) parent);
            }

            int[] childNodeIds = getRootNodeIds();

            if (childNodeIds == null)
            {
                return node;
            }

            for (int id : childNodeIds)
            {
                node.addChild(view, id);
            }

            return node;
        }

        private AccessibilityNodeInfo getVirtualNodeInfo(int virtualViewId)
        {
            var view = resolveGameView();
            if (view == null)
            {
                return null;
            }

            AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();

            if (!populateNodeInfo(node, virtualViewId, view))
            {
                return null;
            }

            return node;
        }

        @Override
        public boolean performAction(int virtualViewId, int action, Bundle arguments)
        {
            if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            {
                return sendEventForVirtualViewId(virtualViewId,
                    AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
            }

            if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
            {
                return sendEventForVirtualViewId(virtualViewId,
                    AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
            }

            if (action == AccessibilityNodeInfo.ACTION_CLICK ||
                action == AccessibilityNodeInfo.ACTION_EXPAND ||
                action == AccessibilityNodeInfo.ACTION_COLLAPSE)
            {
                if (!isNodeInvokable(virtualViewId))
                {
                    return false;
                }

                invokeOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        if (onNodeInvoked(virtualViewId)) {
                            sendEventForVirtualViewId(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
                        }
                    }
                });

                return true;
            }

            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ||
                action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            {
                if (isNodeSlider(virtualViewId))
                {
                    invokeOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                            {
                                onNodeIncremented(virtualViewId);
                            }
                            else
                            {
                                onNodeDecremented(virtualViewId);
                            }

                            // Announce the new value of the accessibility node.
                            sendEventForVirtualViewId(virtualViewId,
                                AccessibilityEvent.TYPE_VIEW_SELECTED);
                        }
                    });

                    return true;
                }

                if (isNodeScrollable(virtualViewId))
                {
                    invokeOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            onNodeScrolled(virtualViewId, action);
                        }
                    });

                    return true;
                }
            }

            if (action == AccessibilityNodeInfo.ACTION_DISMISS)
            {
                if (!isNodeDismissable(virtualViewId))
                {
                    return false;
                }

                invokeOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        onNodeDismissed(virtualViewId);
                    }
                });
                return true;
            }

            return false;
        }
    };

    private class AccessibilityManagerListener implements AccessibilityManager.AccessibilityStateChangeListener, AccessibilityManager.TouchExplorationStateChangeListener
    {
        private boolean m_AccessibilitySetupPending = false;

        // Called during the initialization of the Accessibility module.
        AccessibilityManagerListener()
        {
            m_AccessibilityManager.addAccessibilityStateChangeListener(this);
            m_AccessibilityManager.addTouchExplorationStateChangeListener(this);

            if (m_AccessibilityManager.isEnabled())
            {
                // Post rather than call directly: on AAOS the system fires this callback
                // synchronously during addAccessibilityStateChangeListener above, before
                // the surface view exists. Posting defers it to the next message queue
                // cycle by which point the surface is typically ready.
                new Handler(Looper.getMainLooper()).post(() -> onAccessibilityStateChanged(true));
            }
        }

        // Called during the cleanup of the Accessibility module.
        protected void cleanup()
        {
            m_AccessibilityManager.removeAccessibilityStateChangeListener(this);
            m_AccessibilityManager.removeTouchExplorationStateChangeListener(this);
        }

        @Override
        public void onAccessibilityStateChanged(boolean enabled)
        {
            // The view may be null if getSurfaceView() was called before the surface
            // was created (e.g. when an AAOS accessibility service fires this callback
            // synchronously during addAccessibilityStateChangeListener registration).
            // resolveGameView() retries the lookup so later calls succeed once the
            // view is ready.
            var view = resolveGameView();
            if (view == null)
            {
                if (enabled && !m_AccessibilitySetupPending)
                {
                    m_AccessibilitySetupPending = true;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        m_AccessibilitySetupPending = false;
                        onAccessibilityStateChanged(m_AccessibilityManager.isEnabled());
                    });
                }
                return;
            }
            m_AccessibilitySetupPending = false;

            if (enabled)
            {
                view.setAccessibilityDelegate(UnityAccessibilityDelegate.this);

                // From https://stackoverflow.com/questions/39736842/talkback-with-explorebytouchhelper-doesnt-show-green-square-on-surfaceview-insi.
                view.setWillNotDraw(false);

                // Ensure TouchExploration is also enabled if needed.
                onTouchExplorationStateChanged(m_AccessibilityManager.isTouchExplorationEnabled());
            }
            else
            {
                view.setAccessibilityDelegate(null);
                view.setWillNotDraw(true);

                // Ensure TouchExploration is also disabled.
                onTouchExplorationStateChanged(false);
            }
        }

        @Override
        public void onTouchExplorationStateChanged(boolean enabled)
        {
            var view = resolveGameView();
            if (view == null)
            {
                return;
            }

            boolean isScreenReaderEnabled = m_AccessibilityManager.isEnabled() && enabled;

            if (isScreenReaderEnabled)
            {
                view.setOnHoverListener(new HoverEventListener());
            }
            else
            {
                view.setOnHoverListener(null);
            }

            // onAccessibilityStateChanged and onTouchExplorationStateChanged
            // are also called for accessibility services other than screen
            // readers. We want to send the ScreenReaderStatusChanged
            // notification only when a screen reader service was turned on/off.
            if (m_IsScreenReaderEnabled == isScreenReaderEnabled)
            {
                return;
            }

            m_IsScreenReaderEnabled = isScreenReaderEnabled;

            // Send the ScreenReaderStatusChanged accessibility notification on
            // the Unity thread.
            invokeOnMainThread(new Runnable() {
                @Override
                public void run() {
                    onScreenReaderStatusChanged(isScreenReaderEnabled);
                }
            });
        }
    }

    protected class HoverEventListener implements View.OnHoverListener
    {
        @Override
        public boolean onHover(View v, MotionEvent event)
        {
            switch (event.getAction())
            {
                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_MOVE:
                {
                    // Gets the virtual view id under the finger.
                    int virtualViewId = hitTest(event.getX(), event.getY());

                    setHoveredVirtualViewId(virtualViewId);
                    break;
                }
                case MotionEvent.ACTION_HOVER_EXIT:
                {
                    // Send ACTION_HOVER_EXIT to the last view hovered.
                    setHoveredVirtualViewId(INVALID_ID);
                    break;
                }
                default:
                {
                    android.util.Log.i("a11y", "hover unknown" + event.toString());
                    break;
                }
            }

            return true;
        }
    }

    protected class CaptioningChangeListener extends CaptioningManager.CaptioningChangeListener
    {
        // Called during the initialization of the Accessibility module.
        CaptioningChangeListener()
        {
            m_CaptioningManager.addCaptioningChangeListener(this);
            onEnabledChanged(m_CaptioningManager.isEnabled());
        }

        @Override
        public void onEnabledChanged(boolean enabled)
        {
            invokeOnMainThread(new Runnable() {
                @Override
                public void run() {
                    onClosedCaptioningStatusChanged(enabled);
                }
            });
        }

        // Called during the cleanup of the Accessibility module.
        protected void cleanup()
        {
            m_CaptioningManager.removeCaptioningChangeListener(this);
        }
    }
}
