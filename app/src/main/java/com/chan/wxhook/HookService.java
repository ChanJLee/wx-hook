package com.chan.wxhook;

import android.accessibilityservice.AccessibilityService;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HookService extends AccessibilityService {
	private static final String TAG = "HookService";
	private Handler mHandler;
	private Map<String, Info> mMap = new HashMap<>();

	@Override
	public void onCreate() {
		super.onCreate();
		mHandler = new Handler();
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
		CharSequence pageName = accessibilityEvent.getClassName();
		Log.d(TAG, "enter " + pageName);
		if (TextUtils.equals("com.tencent.mm.ui.LauncherUI", pageName)) {
			handleLauncherUI();
		} else if (TextUtils.equals("com.tencent.mm.plugin.subapp.ui.friend.FMessageConversationUI", pageName)) {
			handleFMessageConversationUI();
		} else if (TextUtils.equals("com.tencent.mm.plugin.subapp.ui.pluginapp.AddMoreFriendsUI", pageName)) {
			handleAddMoreFriendsUI();
		} else if (TextUtils.equals("com.tencent.mm.plugin.account.bind.ui.MobileFriendUI", pageName)) {
			handleMobileFriendUI();
		}
	}

	private void handleLauncherUI() {
		Log.d(TAG, "handleLauncherUI");

		AccessibilityNodeInfo root = getRootInActiveWindow();
		if (root == null) {
			Log.d(TAG, "fetch node info failed");
			return;
		}

		clickedNode(root, "通讯录");
		clickedNode(root, "新的朋友", 2000, true);
	}

	private void handleFMessageConversationUI() {
		Log.d(TAG, "handleFMessageConversationUI");
		AccessibilityNodeInfo root = getRootInActiveWindow();
		if (root == null) {
			Log.d(TAG, "fetch node info failed");
			return;
		}
		clickedNode(root, "添加朋友");
		root.recycle();
	}

	private void handleAddMoreFriendsUI() {
		Log.d(TAG, "handleAddMoreFriendsUI");
		AccessibilityNodeInfo root = getRootInActiveWindow();
		if (root == null) {
			Log.d(TAG, "fetch node info failed");
			return;
		}
		clickedNode(root, "手机联系人");
		root.recycle();
	}

	private void handleMobileFriendUI() {
		Log.d(TAG, "handleMobileFriendUI");
		final AccessibilityNodeInfo root = getRootInActiveWindow();
		List<AccessibilityNodeInfo> scrollViews = root.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/bcs");
		if (scrollViews == null || scrollViews.isEmpty()) {
			return;
		}

		AccessibilityNodeInfo scrollView = scrollViews.get(scrollViews.size() - 1);
		Log.d(TAG, "can scroll: " + scrollView.isScrollable());

		do {
			for (int i = 0; i < scrollView.getChildCount(); ++i) {
				AccessibilityNodeInfo item = scrollView.getChild(i);
				List<AccessibilityNodeInfo> contacts = item.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/bgl");
				List<AccessibilityNodeInfo> nicknames = item.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/bgm");
				if (contacts == null || nicknames == null || contacts.isEmpty() || nicknames.isEmpty()) {
					continue;
				}

				String contactsName = String.valueOf(contacts.get(contacts.size() - 1).getText());
				if (!mMap.containsKey(contactsName)) {
					String nickname = String.valueOf(nicknames.get(nicknames.size() - 1).getText());
					nickname = nickname.replaceAll("微信:", "");
					mMap.put(contactsName, new Info(nickname, contactsName));
				}
			}
			Log.d(TAG, "size -> " + mMap.size());
		} while (scrollView.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD));

		File file = new File(Environment.getExternalStorageDirectory(), "output.txt");
		BufferedWriter bufferedWriter = null;
		try {
			bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
			bufferedWriter.write("[");
			for (Info info : mMap.values()) {
				bufferedWriter.write(String.format("{\"contacts\": \"%s\", \"nickname\": \"%s\"},", info.contacts, info.nickname));
			}
			bufferedWriter.write("]");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (bufferedWriter != null) {
					bufferedWriter.flush();
					bufferedWriter.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		root.recycle();
	}

	@Override
	public void onInterrupt() {
		Log.d(TAG, "onInterrupt");
	}

	private void clickedNode(AccessibilityNodeInfo root, String text) {
		clickedNode(root, text, 0, false);
	}

	private void clickedNode(final AccessibilityNodeInfo root, final String text, long delay, final boolean recycle) {
		if (delay <= 0) {
			doClickedNode(root, text);
			if (recycle) {
				root.recycle();
			}
			return;
		}

		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				doClickedNode(root, text);
				if (recycle) {
					root.recycle();
				}
			}
		}, delay);
	}

	private void doClickedNode(AccessibilityNodeInfo root, String text) {
		List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
		if (list == null || list.isEmpty()) {
			Log.d(TAG, "find node " + text + " failed");
			return;
		}

		AccessibilityNodeInfo target = list.get(list.size() - 1);
		while (target != null && !target.isClickable()) {
			target = target.getParent();
		}

		if (target != null) {
			target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
		}
	}
}
