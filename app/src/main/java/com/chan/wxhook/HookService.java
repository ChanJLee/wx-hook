package com.chan.wxhook;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HookService extends AccessibilityService {
	private static final String TAG = "HookService";
	private Handler mHandler;
	private Info mCurrentInfo = new Info();
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
		} else if (TextUtils.equals("com.tencent.mm.plugin.fts.ui.FTSAddFriendUI", pageName)) {
			handleFTSAddFriendUI();
		} else if (TextUtils.equals("com.tencent.mm.plugin.profile.ui.ContactInfoUI", pageName)) {
			handleSearchSuccess();
		} else if (TextUtils.equals("com.tencent.mm.plugin.profile.ui.ContactMoreInfoUI", pageName)) {
			handleContactMoreInfo();
		}
	}

	private void handleContactMoreInfo() {
		Log.d(TAG, "handleContactMoreInfo");
		AccessibilityNodeInfo root = getRootInActiveWindow();
		AccessibilityNodeInfo extraInfo = findNodeById(root, "com.tencent.mm:id/cxa");
		if (extraInfo != null && mCurrentInfo != null) {
			mCurrentInfo.extraInfo = String.valueOf(extraInfo.getText());
			Toast.makeText(this, new Gson().toJson(mCurrentInfo), Toast.LENGTH_SHORT).show();
		}
	}

	private void handleSearchSuccess() {
		Log.d(TAG, "handleSearchSuccess");
		if (mCurrentInfo == null) {
			return;
		}

		AccessibilityNodeInfo root = getRootInActiveWindow();

		AccessibilityNodeInfo nickname = findNodeById(root, "com.tencent.mm:id/art");
		if (nickname != null && !TextUtils.isEmpty(nickname.getText())) {
			mCurrentInfo.nickname = String.valueOf(nickname.getText()).replace("微信:", "");
		}

		AccessibilityNodeInfo gender = findNodeById(root, "com.tencent.mm:id/as4");
		if (gender != null) {
			mCurrentInfo.gender = String.valueOf(gender.getContentDescription());
		}

		AccessibilityNodeInfo more = findNodeById(root, "com.tencent.mm:id/ci");
		if (more == null) {
			Toast.makeText(this, new Gson().toJson(mCurrentInfo), Toast.LENGTH_SHORT).show();
			root.recycle();
			return;
		}

		clickedNodeById(root, "com.tencent.mm:id/ci");
		root.recycle();
	}

	private void handleFTSAddFriendUI() {
		Log.d(TAG, "handleFTSAddFriendUI");
		AccessibilityNodeInfo root = getRootInActiveWindow();
		AccessibilityNodeInfo inputNode = findNodeById(root, "com.tencent.mm:id/jd");
		if (inputNode == null) {
			root.recycle();
			return;
		}

		mCurrentInfo = new Info();
		mCurrentInfo.contacts = "18280097259";
		Bundle arguments = new Bundle();
		arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, mCurrentInfo.contacts);
		arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD);
		arguments.putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, true);
		inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
		inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
		inputNode.performAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, arguments);

		String targetText = "搜索:" + mCurrentInfo.contacts;
		AccessibilityNodeInfo searchNode = findNodeByText(root, targetText);
		if (searchNode == null) {
			Toast.makeText(this, "没有找到 " + mCurrentInfo.contacts, Toast.LENGTH_SHORT).show();
			root.recycle();
			return;
		}

		clickedNodeByText(root, targetText, 200, true);
	}

	private void handleLauncherUI() {
		// 首页
		Log.d(TAG, "handleLauncherUI");

		AccessibilityNodeInfo root = getRootInActiveWindow();
		if (root == null) {
			Log.d(TAG, "fetch node info failed");
			return;
		}

		clickedNodeByText(root, "通讯录");
		clickedNodeByText(root, "新的朋友", 2000, true);
	}

	private void handleFMessageConversationUI() {
		Log.d(TAG, "handleFMessageConversationUI");
		AccessibilityNodeInfo root = getRootInActiveWindow();
		if (root == null) {
			Log.d(TAG, "fetch node info failed");
			return;
		}
		clickedNodeById(root, "com.tencent.mm:id/bf2");
		root.recycle();
	}

	private void handleMobileFriendUI() {
//		Log.d(TAG, "handleMobileFriendUI");
//		final AccessibilityNodeInfo root = getRootInActiveWindow();
//		AccessibilityNodeInfo scrollView = findNodeById(root, "com.tencent.mm:id/bcs");
//		if (scrollView == null) {
//			root.recycle();
//			return;
//		}
//
//		Log.d(TAG, "can scroll: " + scrollView.isScrollable());
//		do {
//			for (int i = 0; i < scrollView.getChildCount(); ++i) {
//				AccessibilityNodeInfo item = scrollView.getChild(i);
//				List<AccessibilityNodeInfo> contacts = item.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/bgl");
//				List<AccessibilityNodeInfo> nicknames = item.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/bgm");
//				if (contacts == null || nicknames == null || contacts.isEmpty() || nicknames.isEmpty()) {
//					continue;
//				}
//
//				String contactsName = String.valueOf(contacts.get(contacts.size() - 1).getText());
//				if (!mMap.containsKey(contactsName)) {
//					String nickname = String.valueOf(nicknames.get(nicknames.size() - 1).getText());
//					nickname = nickname.replaceAll("微信:", "");
//					mMap.put(contactsName, new Info(nickname, contactsName));
//				}
//			}
//			Log.d(TAG, "size -> " + mMap.size());
//		} while (scrollView.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD));
//
//		File file = new File(Environment.getExternalStorageDirectory(), "output.txt");
//		BufferedWriter bufferedWriter = null;
//		try {
//			bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
//			bufferedWriter.write("[");
//			for (Info info : mMap.values()) {
//				bufferedWriter.write(String.format("{\"contacts\": \"%s\", \"nickname\": \"%s\"},", info.contacts, info.nickname));
//			}
//			bufferedWriter.write("]");
//		} catch (Exception e) {
//			e.printStackTrace();
//		} finally {
//			try {
//				if (bufferedWriter != null) {
//					bufferedWriter.flush();
//					bufferedWriter.close();
//				}
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//		}
//
//		root.recycle();
	}

	@Override
	public void onInterrupt() {
		Log.d(TAG, "onInterrupt");
	}

	private void clickedNodeById(AccessibilityNodeInfo root, String id) {
		AccessibilityNodeInfo target = findNodeById(root, id);
		if (target == null) {
			Log.d(TAG, "find node by id " + id + " failed");
			return;
		}

		while (target != null && !target.isClickable()) {
			target = target.getParent();
		}

		if (target != null) {
			target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
		}
	}

	private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo root, String id) {
		List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(id);
		if (list == null || list.isEmpty()) {
			return null;
		}
		return list.get(list.size() - 1);
	}

	private void clickedNodeByText(AccessibilityNodeInfo root, String text) {
		clickedNodeByText(root, text, 0, false);
	}

	private void clickedNodeByText(final AccessibilityNodeInfo root, final String text, long delay, final boolean recycle) {
		if (delay <= 0) {
			doClickedNodeByText(root, text);
			if (recycle) {
				root.recycle();
			}
			return;
		}

		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				doClickedNodeByText(root, text);
				if (recycle) {
					root.recycle();
				}
			}
		}, delay);
	}

	private void doClickedNodeByText(AccessibilityNodeInfo root, String text) {
		AccessibilityNodeInfo target = findNodeByText(root, text);
		if (target == null) {
			Log.d(TAG, "find node by text " + text + " failed");
			return;
		}
		while (target != null && !target.isClickable()) {
			target = target.getParent();
		}

		if (target != null) {
			target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
		}
	}

	private AccessibilityNodeInfo findNodeByText(AccessibilityNodeInfo root, String text) {
		List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
		if (list == null || list.isEmpty()) {
			return null;
		}
		return list.get(list.size() - 1);
	}
}
