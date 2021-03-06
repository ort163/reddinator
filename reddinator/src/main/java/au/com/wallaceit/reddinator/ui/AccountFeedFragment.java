/*
 * Copyright 2013 Michael Boyde Wallace (http://wallaceit.com.au)
 * This file is part of Reddinator.
 *
 * Reddinator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Reddinator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Reddinator (COPYING). If not, see <http://www.gnu.org/licenses/>.
 */

package au.com.wallaceit.reddinator.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import au.com.wallaceit.reddinator.R;
import au.com.wallaceit.reddinator.Reddinator;
import au.com.wallaceit.reddinator.activity.AccountActivity;
import au.com.wallaceit.reddinator.activity.CommentsContextDialogActivity;
import au.com.wallaceit.reddinator.activity.MessagesActivity;
import au.com.wallaceit.reddinator.activity.ViewRedditActivity;
import au.com.wallaceit.reddinator.core.RedditData;
import au.com.wallaceit.reddinator.core.ThemeManager;
import au.com.wallaceit.reddinator.core.Utilities;
import au.com.wallaceit.reddinator.service.WidgetProvider;
import au.com.wallaceit.reddinator.tasks.CommentTask;
import au.com.wallaceit.reddinator.tasks.ComposeMessageTask;
import au.com.wallaceit.reddinator.tasks.HidePostTask;
import au.com.wallaceit.reddinator.tasks.MarkMessageTask;
import au.com.wallaceit.reddinator.tasks.SavePostTask;
import au.com.wallaceit.reddinator.tasks.VoteTask;

public class AccountFeedFragment extends Fragment implements VoteTask.Callback, CommentTask.Callback, ComposeMessageTask.Callback {
    private Resources resources;
    private WebView mWebView;
    private boolean mFirstTime = true;
    private LinearLayout ll;
    private Reddinator global;
    private boolean isMessages = false;
    private String type; // end part of the reddit url ie. overview, upvoted, downvoted, inbox, sent etc
    private String currentSort = "new";
    private FeedLoader feedLoader;
    private VoteTask commentsVoteTask;
    private CommentTask commentTask;

    public static AccountFeedFragment init(String type, boolean load) {
        AccountFeedFragment commentsTab = new AccountFeedFragment();
        Bundle args = new Bundle();
        args.putBoolean("load", load);
        args.putString("type", type);
        commentsTab.setArguments(args);
        return commentsTab;
    }

    private boolean loaded = false;
    public void load(){
        if (!loaded) {
            loadComments("new");
            loaded = true;
        }
    }

    public void reload(){
        mWebView.loadUrl("javascript:loadFeedStart();");
        loadComments(null);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context mContext = this.getActivity();
        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        global = (Reddinator) mContext.getApplicationContext();
        resources = getResources();
        final boolean load = getArguments().getBoolean("load");
        type = getArguments().getString("type");

        ll = new LinearLayout(mContext);
        ll.setLayoutParams(new WebView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 0, 0));
        // fixes for activity_webview not taking keyboard input on some devices
        mWebView = new RWebView(mContext);
        mWebView.setLayoutParams(new WebView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 0, 0));
        int backgroundColor = Color.parseColor(((ActivityInterface) getActivity()).getCurrentTheme().getValue("background_color"));
        mWebView.setBackgroundColor(backgroundColor);
        ll.addView(mWebView);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true); // enable ecmascript
        webSettings.setDomStorageEnabled(true); // some video sites require dom storage
        webSettings.setSupportZoom(false);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        int fontSize = Integer.parseInt(mSharedPreferences.getString("commentfontpref", "18"));
        webSettings.setDefaultFontSize(fontSize);
        //webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        mWebView.setFocusable(true);
        mWebView.setFocusableInTouchMode(true);
        mWebView.requestFocus(View.FOCUS_DOWN);

        mSharedPreferences.getString("titlefontpref", "16");

        final String themeStr = global.mThemeManager.getActiveTheme("appthemepref").getValuesString(true);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {

                global.handleLink(getContext(), url);
                return true; // always override url
            }

            public void onPageFinished(WebView view, String url) {
                mWebView.loadUrl("javascript:init(\"" + StringEscapeUtils.escapeEcmaScript(themeStr) + "\", \"" + global.mRedditData.getUsername() + "\", \""+type+"\")");
                if (load) load();
            }
        });
        mWebView.setWebChromeClient(new WebChromeClient());

        mWebView.requestFocus(View.FOCUS_DOWN);
        WebInterface webInterface = new WebInterface(mContext);
        mWebView.addJavascriptInterface(webInterface, "Reddinator");
        getActivity().registerForContextMenu(mWebView);

        if (type.equals("unread") || type.equals("inbox") || type.equals("sent"))
            isMessages = true;

        mWebView.loadUrl("file:///android_asset/"+(isMessages?"messages":"account")+".html");
    }

    public void updateTheme() {
        String themeStr = ((ActivityInterface) getActivity()).getCurrentTheme().getValuesString(true);
        Utilities.executeJavascriptInWebview(mWebView, "setTheme(\"" + StringEscapeUtils.escapeEcmaScript(themeStr) + "\")");
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (container == null) {
            return null;
        }
        if (mFirstTime) {
            mFirstTime = false;
        }

        return ll;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //mWebView.saveState(outState);
    }

    @Override
    public void onPause() {
        super.onPause();
        //mWebView.saveState(WVState);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if (feedLoader !=null)
            feedLoader.cancel(true);
        if (commentsVoteTask!=null)
            commentsVoteTask.cancel(false);
        if (commentTask!=null)
            commentTask.cancel(false);
    }

    @Override
    public void onVoteComplete(boolean result, RedditData.RedditApiException exception, String redditId, int direction, int netVote, int listPosition) {
        ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.app_name)); // reset title
        if (result) {
            mWebView.loadUrl("javascript:voteCallback(\"" + redditId + "\", \"" + direction + "\", "+netVote+")");
        } else {
            // check login required
            if (exception.isAuthError()) global.mRedditData.initiateLogin(getActivity(), false);
            // show error
            Utilities.showApiErrorToastOrDialog(getActivity(), exception);
        }
    }

    @Override
    public void onCommentComplete(JSONObject result, RedditData.RedditApiException exception, int action, String redditId) {
        ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.app_name)); // reset title
        if (result!=null){
            switch (action){
                case -1:
                    mWebView.loadUrl("javascript:deleteCallback(\"" + redditId + "\")");
                    break;
                case 0:
                    mWebView.loadUrl("javascript:commentCallback(\"" + redditId + "\", \"" + StringEscapeUtils.escapeEcmaScript(result.toString()) + "\")");
                    break;
                case 1:
                    mWebView.loadUrl("javascript:editCallback(\"" + redditId + "\", \"" + StringEscapeUtils.escapeEcmaScript(result.toString()) + "\")");
                    break;
            }
        } else {
            // check login required
            if (exception.isAuthError()) global.mRedditData.initiateLogin(getActivity(), false);
            // show error
            Utilities.showApiErrorToastOrDialog(getActivity(), exception);
            mWebView.loadUrl("javascript:commentCallback(\"" + redditId + "\", false)");
        }
    }

    @Override
    public void onMessageSent(boolean result, RedditData.RedditApiException exception, String[] args) {
        ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.app_name)); // reset title
        if (result){
            mWebView.loadUrl("javascript:messageCallback(\"" + args[4] + "\", true);");
            // reload sent feed
            if (isMessages)
                ((MessagesActivity) getActivity()).reloadSentMessages();
            Toast.makeText(getActivity(), resources.getString(R.string.message_sent), Toast.LENGTH_LONG).show();
        } else {
            Utilities.showApiErrorToastOrDialog(getActivity(), exception);
        }
    }

    public class WebInterface {
        Context mContext;

        /**
         * Instantiate the interface and set the context
         */
        WebInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void reloadFeed(String sort) {
            loadComments(sort);
        }

        @JavascriptInterface
        public void loadMore(String moreId) {
            feedLoader = new FeedLoader(currentSort, moreId);
            feedLoader.execute();
        }

        @JavascriptInterface
        public void vote(String thingId, int direction, int currentVote) {
            ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.voting));
            commentsVoteTask = new VoteTask(global, AccountFeedFragment.this, thingId, direction, currentVote);
            commentsVoteTask.execute();
        }

        @JavascriptInterface
        public void message(String to, String subject, String text, String id) {
            ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.submitting));
            ComposeMessageTask messageTask = new ComposeMessageTask(global, AccountFeedFragment.this, new String[]{to, subject, text, null, id});
            messageTask.execute();
        }

        @JavascriptInterface
        public void comment(String parentId, String text) {
            ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.submitting));
            commentTask = new CommentTask(global, parentId, text, CommentTask.ACTION_ADD, AccountFeedFragment.this);
            commentTask.execute();
        }

        @JavascriptInterface
        public void edit(String thingId, String text) {
            ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.submitting));
            commentTask = new CommentTask(global, thingId, text, CommentTask.ACTION_EDIT, AccountFeedFragment.this);
            commentTask.execute();
        }

        @JavascriptInterface
        public void delete(String thingId) {
            ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.deleting));
            commentTask = new CommentTask(global, thingId, null, CommentTask.ACTION_DELETE, AccountFeedFragment.this);
            commentTask.execute();
        }

        @JavascriptInterface
        public void unSave(final String thingId) {
            ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.deleting));
            SavePostTask savePostTask = new SavePostTask(getActivity(), false, new Runnable() {
                @Override
                public void run() {
                    ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.app_name)); // reset title
                    mWebView.loadUrl("javascript:deleteCallback('"+thingId+"')");
                }
            });
            savePostTask.execute("unsave", thingId);
        }

        @JavascriptInterface
        public void unHide(final String thingId) {
            ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.deleting));
            HidePostTask hidePostTask = new HidePostTask(getActivity(), false, new Runnable() {
                @Override
                public void run() {
                    ((ActivityInterface) getActivity()).setTitleText(resources.getString(R.string.app_name)); // reset title
                    mWebView.loadUrl("javascript:deleteCallback('"+thingId+"')");
                }
            });
            hidePostTask.execute("unhide", thingId);
        }

        @JavascriptInterface
        public void openCommentLink(String link) {
            Intent intent = new Intent(mContext, CommentsContextDialogActivity.class);
            intent.setData(Uri.parse("https://www.reddit.com" + link));
            startActivity(intent);
        }

        @JavascriptInterface
        public void openRedditPost(String redditId, String postUrl, String permaLink, String userLikes) {
            Intent intent = new Intent(mContext, ViewRedditActivity.class);
            intent.putExtra(Reddinator.ITEM_ID, redditId);
            intent.putExtra(Reddinator.ITEM_URL, postUrl);
            intent.putExtra(Reddinator.ITEM_PERMALINK, permaLink);
            intent.putExtra(Reddinator.ITEM_USERLIKES, userLikes);
            startActivity(intent);
        }

        @JavascriptInterface
        public void archiveToast() {
            Toast.makeText(getActivity(), R.string.archived_post_error, Toast.LENGTH_LONG).show();
        }
    }

    public interface ActivityInterface {
        void setTitleText(String titleText);
        ThemeManager.Theme getCurrentTheme();
    }

    private void loadComments(String sort) {
        if (sort != null)
            currentSort = sort;
        feedLoader = new FeedLoader(currentSort);
        feedLoader.execute();
    }

    class FeedLoader extends AsyncTask<Void, Integer, String> {

        private boolean loadMore = false;
        private String mSort = "best";
        private String mMoreId = null;
        private ArrayList<String> unreadIds = null;
        private RedditData.RedditApiException exception;

        FeedLoader(String sort){
            mSort = sort;
        }

        FeedLoader(String sort, String moreId) {
            mSort = sort;
            if (moreId != null && !moreId.equals("")) {
                loadMore = true;
                mMoreId = moreId;
            }
        }

        @Override
        protected String doInBackground(Void... none) {
            JSONArray data;
            try {
                if (isMessages) {
                    JSONArray cached = global.getUnreadMessages();
                    if (type.equals("unread") && cached.length()>0){
                        data = cached;
                    } else {
                        data = global.mRedditData.getMessageFeed(type, 25, mMoreId);
                    }
                    // collect ids of unread messages to mark them read below
                    if (type.equals("unread") && data.length()>0){
                        unreadIds = new ArrayList<>();
                        for (int i=0; i<data.length(); i++){
                            try {
                                unreadIds.add(data.getJSONObject(i).getJSONObject("data").getString("name"));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    data = global.mRedditData.getAccountFeed(type, mSort, 25, mMoreId);
                }
            } catch (RedditData.RedditApiException e) {
                e.printStackTrace();
                exception = e;
                return "-1"; // Indicate error
            }

            if (data.length()>0) {
                return data.toString();
            }

            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            switch (result) {
                case "":
                    if (!loadMore) {
                        executeJavascript("showLoadingView(\""+StringEscapeUtils.escapeEcmaScript(resources.getString(R.string.nothing_more_here))+"\");");
                    } else {
                        executeJavascript("noMoreCallback('" + mMoreId + "');");
                    }
                    break;
                case "-1":
                    // show error
                    if (!loadMore) {
                        executeJavascript("showLoadingView('" + resources.getString(R.string.error_loading_feed) + "');");
                    } else {
                        // reset load more button
                        executeJavascript("resetMoreClickEvent('" + mMoreId + "');");
                    }
                    // check login required
                    if (exception.isAuthError()) global.mRedditData.initiateLogin(getActivity(), false);

                    Utilities.showApiErrorToastOrDialog(getActivity(), exception);
                    break;
                default:
                    executeJavascript("populateFeed('" + StringEscapeUtils.escapeEcmaScript(result) + "', "+loadMore+");");
                    // Mark messages read; this clears cached messages and count once completed
                    if (unreadIds!=null && unreadIds.size()>0){
                        new MarkMessageTask(global, unreadIds).execute();
                    }
                    break;
            }
        }
    }

    private void executeJavascript(String javascript){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mWebView.evaluateJavascript(javascript, null);
        } else {
            mWebView.loadUrl("javascript:"+javascript);
        }
    }
}
