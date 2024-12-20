package net.swigglesoft.shackbrowse;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.app.ListFragment;

import androidx.collection.LruCache;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.text.ClipboardManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.common.MediaItem;
import androidx.media3.ui.AspectRatioFrameLayout;

import com.nhaarman.listviewanimations.itemmanipulation.ExpandCollapseListener;

import net.swigglesoft.CheckableTableLayout;
import net.swigglesoft.CustomLinkMovementMethod;
import net.swigglesoft.ExpandableListItemAdapter;
import net.swigglesoft.FixedTextView;

import static net.swigglesoft.shackbrowse.ShackApi.POST_EXPIRY_HOURS;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;


import static net.swigglesoft.shackbrowse.LoadingSplashFragment.randInt;
import static net.swigglesoft.shackbrowse.StatsFragment.statInc;
import static net.swigglesoft.shackbrowse.StatsFragment.statMax;
import static net.swigglesoft.shackbrowse.notifier.NotifierReceiver.checkIfMuted;
import static net.swigglesoft.shackbrowse.notifier.NotifierReceiver.toggleMuted;

public class ThreadViewFragment extends ListFragment {
    public PostLoadingAdapter _adapter;
    int _rootPostId = 0;
    // int _currentPostId = 0;
    int _selectPostIdAfterLoading = 0;
    // String _currentPostAuthor;
    boolean _highlighting = false;
    public String _highlight = "";

    int _lastExpanded = 0;

    private int _userNameHeight = 0;

    boolean _touchedFavoritesButton = false;

    MaterialDialog _progressDialog;

    // list view saved state while rotating
    private Parcelable _listState = null;
    private int _listPosition = 0;
    private int _itemPosition = 0;
    private int _itemChecked = ListView.INVALID_POSITION;

    JSONObject _lastThreadJson;

    private boolean _refreshRestore = false;
    private boolean _viewAvailable;

    private int _postYLoc = 0;
    boolean _autoFaveOnLoad = false;
    int _messageId = 0;
    String _messageSubject;
    private AsyncTask<String, Void, Integer> _curTask;
    protected boolean _showFavSaved = false;
    protected boolean _showUnFavSaved = false;
    private SwipeRefreshLayout ptrLayout;
    private SharedPreferences _prefs;
    private boolean _isSelectPostIdAfterLoadingIdaPQPId = false;
    private boolean _showThreadExpired = false;

    public Post _loadPostAfterAdapterReady;
    private MainActivity mMainActivity;


    public int getPostId() {
        return _rootPostId;
    }

    @Override
    public void onCreate(Bundle savedInstanceBundle) {

        super.onCreate(savedInstanceBundle);
        this.setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        _viewAvailable = true;
        return inflater.inflate(R.layout.thread_view, null);
    }

    @Override
    public void onDestroyView() {
        _viewAvailable = false;
        super.onDestroyView();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mMainActivity = (MainActivity) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mMainActivity = null;
    }

    public void loadPost(Post post) {
        _adapter.add(post);

        // needs to be displaypost(position) not (post)
        expandAndCheckPostWithoutAnimation(_adapter.indexOf(post));
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // set list view up
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        getListView().setDividerHeight(0);

        //getListView().setBackgroundColor(getResources().getColor(R.color.collapsed_postbg));
        _prefs = PreferenceManager.getDefaultSharedPreferences(mMainActivity);

        if (_adapter == null) {
            // first launch, try to set everything up
            Bundle args = getArguments();
            String action = mMainActivity.getIntent().getAction();
            Uri uri = mMainActivity.getIntent().getData();

            // instantiate adapter
            _adapter = new PostLoadingAdapter(mMainActivity, new ArrayList<Post>());
            setListAdapter(_adapter);
            _adapter.setAbsListView(getListView());
            _adapter.setTitleViewOnClickListener(getTitleViewOnClickListener());
            _adapter.setExpandCollapseListener(_adapter.mExpandCollapseListener);
            _adapter.setLimit(2);

            //  only load this junk if the arguments isn't null
            if (args != null) {
                if (args.containsKey("rootPostId")) {
                    _rootPostId = args.getInt("rootPostId");
                    _itemPosition = 0;
                }
                if (args.containsKey("messageId")) {
                    _messageId = args.getInt("messageId");
                    _messageSubject = args.getString("messageSubject");
                }
                if (args.containsKey("autoFaveOnLoad")) {
                    _autoFaveOnLoad = args.getBoolean("autoFaveOnLoad");
                }
                if (args.containsKey("selectPostIdAfterLoading")) {
                    _selectPostIdAfterLoading = args.getInt("selectPostIdAfterLoading");
                }
                if (args.containsKey("content")) {
                    String userName = args.getString("userName");
                    String postContent = args.getString("content");
                    Long posted = args.getLong("posted");
                    String moderation = args.containsKey("moderation") ? args.getString("moderation") : "";
                    String lastThreadJson = args.containsKey("json") ? args.getString("json") : "";
                    try {
                        _lastThreadJson = new JSONObject(lastThreadJson);
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    // create root post fast with no load delay
                    Post post = null;
                    if (_messageId > 0)
                        post = new Post(_messageId, userName, postContent, posted, 0, moderation, true);
                    else
                        post = new Post(_rootPostId, userName, postContent, posted, 0, moderation, true);

                    loadPost(post);
                } else if (action != null && action.equals(Intent.ACTION_VIEW) && uri != null) {
                    String id = uri.getQueryParameter("id");
                    if (id == null) {
                        ErrorDialog.display(mMainActivity, "Error", "Invalid URL Found");
                        return;
                    }

                    _rootPostId = Integer.parseInt(id);
                    _itemPosition = 0;
                }
            }
            if (_loadPostAfterAdapterReady != null)
                loadPost(_loadPostAfterAdapterReady);

            _adapter.triggerLoadMore();
        } else {
            // user rotated the screen, try to go back to where they where
            restoreListState();
        }

        // pull to fresh integration
        // Retrieve the PullToRefreshLayout from the content view
        ptrLayout = getView().findViewById(R.id.tview_swiperefresh);
        MainActivity.setupSwipeRefreshColors(getActivity(), ptrLayout);
        // Give the PullToRefreshAttacher to the PullToRefreshLayout, along with a refresh listener.
        ptrLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshThreadReplies();
            }
        });

        updateThreadViewUi();
    }

    public void updateThreadViewUi() {
        if (_viewAvailable) {
            if (getListView() != null) {
                // handle the throbbers
                if (_adapter != null) {
                    if (_adapter.getCount() == 1) {
                        // single post already loaded throbber
                        _adapter.notifyDataSetChanged();
                    }
                }
                if (_messageId != 0) {
                    // messages mode
                    getView().findViewById(R.id.tview_FSIcon).setVisibility(View.GONE);
                    getListView().setVisibility(View.VISIBLE);
                    _showFavSaved = false;
                    _showUnFavSaved = false;
                    // disable PTR for message mode
                    ptrLayout.setEnabled(false);
                } else if (_messageId == 0) {
                    System.out.println("TVIEW: SHOW FSICON");
                    // show the icon and start message if no threads or messages have been loaded
                    getView().findViewById(R.id.tview_FSIcon).setVisibility((_rootPostId > 0) ? View.GONE : View.VISIBLE);
                    getListView().setVisibility((_rootPostId > 0) ? View.VISIBLE : View.GONE);
                    System.out.println("TVIEW: LISTVIEW " + ((_rootPostId > 0) ? View.VISIBLE : View.GONE));

                    getView().findViewById(R.id.tview_FSIcon).setOnClickListener(o -> {
                        Toast.makeText(getActivity(), "TVIEW:LVVIS " + getListView().getVisibility(), Toast.LENGTH_LONG).show();
                        getListView().setVisibility(View.GONE);
                    });


                    // and provided a way to save thread
                    if ((_lastThreadJson != null) && (_adapter != null) && (_adapter.getCount() > 0) && (_adapter.getItem(0) != null)) {
                        // determine if checked
                        boolean set2 = mMainActivity.mOffline.containsThreadId(ThreadViewFragment.this._rootPostId);

                        _showFavSaved = set2;
                        _showUnFavSaved = !set2;
                    } else {
                        _showFavSaved = false;
                        _showUnFavSaved = false;
                    }

                    // enable PTR because we are not in message mode
                    if (ptrLayout != null)
                        ptrLayout.setEnabled(true);
                }

                // handle the fullscreen throbber
                RelativeLayout FSLoad = (RelativeLayout) getView().findViewById(R.id.tview_FSLoad);
                if ((_adapter != null) && (_adapter.isCurrentlyLoading()) && (_adapter.getCount() == 0)) {
                    ptrLayout.setEnabled(false);
                    getListView().setVisibility(View.GONE);
                    FSLoad.setVisibility(View.VISIBLE);
                } else {
                    if ((_rootPostId > 0) || (_messageId > 0)) {
                        ptrLayout.setEnabled(true);
                        getListView().setVisibility(View.VISIBLE);
                    }
                    FSLoad.setVisibility(View.GONE);
                }

                // update options menu
                mMainActivity.invalidateOptionsMenu();


                // expired thread banner
                _showThreadExpired = false;
                final boolean noCollapseAnim = !mMainActivity.getDualPane();

                // adapter exists, item 0 exists, and not in message mode
                if (_adapter != null && _adapter.getCount() != 0 && _adapter.getItem(0) != null && _messageId == 0) {
                    if (TimeDisplay.threadAgeInHours(_adapter.getItem(0).getPosted()) > POST_EXPIRY_HOURS) {
                        _showThreadExpired = true;
                    }
                }
                // update thread expired
                if (_showThreadExpired && _prefs.getBoolean("showThreadExpiredAlert", true)) {
                    expandView(getView().findViewById(R.id.tview_thread_expired));
                    getView().findViewById(R.id.tview_button_te_ok).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (_prefs.getBoolean("showThreadExpiredOkDialog", true)) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(mMainActivity);
                                builder.setTitle("Expired Thread");
                                final View v2 = v;
                                LayoutInflater annoyInflater = LayoutInflater.from(mMainActivity);
                                View annoyLayout = annoyInflater.inflate(R.layout.dialog_nevershowagain, null);
                                final CheckBox dontShowAgain = (CheckBox) annoyLayout.findViewById(R.id.skip);
                                final CheckBox dontShowAgain2 = (CheckBox) annoyLayout.findViewById(R.id.skip2);
                                dontShowAgain2.setVisibility(View.VISIBLE);
                                ((TextView) annoyLayout.findViewById(R.id.annoy_text)).setText("If you post in this thread, it may not be seen by anyone, as the thread is no longer in the latest chatty thread list.");
                                ((TextView) annoyLayout.findViewById(R.id.skip)).setText("Don't show the alert banner");
                                ((TextView) annoyLayout.findViewById(R.id.skip2)).setText("Don't show this dialog when I dismiss the banner");
                                builder.setView(annoyLayout)
                                        // Set the action buttons
                                        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int id) {
                                                if (dontShowAgain2.isChecked()) {
                                                    SharedPreferences.Editor edit = _prefs.edit();
                                                    edit.putBoolean("showThreadExpiredOkDialog", false);
                                                    edit.commit();
                                                }
                                                if (dontShowAgain.isChecked()) {
                                                    SharedPreferences.Editor edit = _prefs.edit();
                                                    edit.putBoolean("showThreadExpiredAlert", false);
                                                    edit.commit();
                                                }
                                                collapseView(v2.getRootView().findViewById(R.id.tview_thread_expired), false);
                                            }
                                        });

                                AlertDialog dialog = builder.create();//AlertDialog dialog; create like this outside onClick
                                dialog.show();
                            } else
                                collapseView(getView().findViewById(R.id.tview_thread_expired), false);
                        }
                    });
                } else
                    collapseView(getView().findViewById(R.id.tview_thread_expired), noCollapseAnim);
            }
        }
    }

    private void searchForPosts(String term) {
        Bundle args = new Bundle();
        args.putString("author", term);
        mMainActivity.openSearch(args);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // we should put this info into the outState, but the compatibility framework
        // seems to swallow it somewhere
        saveListState();
    }

    public void saveListState() {
        if (_viewAvailable) {
            ListView listView = getListView();
            _listState = listView.onSaveInstanceState();
            _listPosition = listView.getFirstVisiblePosition();
            _itemChecked = _lastExpanded;
            View itemView = listView.getChildAt(0);
            _itemPosition = itemView == null ? 0 : itemView.getTop();
        }
    }

    public void restoreListState() {
        if (_viewAvailable) {
            if (_listState != null)
                getListView().onRestoreInstanceState(_listState);

            getListView().setSelectionFromTop(_listPosition, _itemPosition);
            if (_itemChecked != ListView.INVALID_POSITION)
                expandAndCheckPostWithoutAnimation(_itemChecked);
        }
    }

    public void ensurePostSelectedAndDisplayed() {
        ensurePostSelectedAndDisplayed(_selectPostIdAfterLoading, false);
    }

    public void ensurePostSelectedAndDisplayed(int postId, final boolean withAnimation) {
        System.out.println("ENSURESELECTED " + postId);

        int length = _adapter.getCount();
        for (int i = 0; i < length; i++) {
            Post post = _adapter.getItem(i);
            if (post != null && post.getPostId() == postId) {
                getListView().setSelectionFromTop(i, 0);
                //ensureVisible(i, true, false, true);
                final int pos = i;
                if (withAnimation) {
                    getListView().post(new Runnable() {
                        @Override
                        public void run() {
                            expandAndCheckPost(pos);
                        }
                    });
                } else {
                    expandAndCheckPostWithoutAnimation(pos);
                }
                getListView().post(new Runnable() {
                    @Override
                    public void run() {
                        getListView().setSelectionFromTop(pos, 0);
                    }
                });
                System.out.println("ENSURESELECTED ECP " + postId + " " + i);
                // dont select root posts. unnecessary
                // i is position


                if (_refreshRestore) {
                    getListView().setSelectionFromTop(_listPosition, _itemPosition);
                    getListView().post(new Runnable() {
                        @Override
                        public void run() {
                            getListView().setSelectionFromTop(_listPosition, _itemPosition);
                            // getListView().smoothScrollToPositionFromTop(_listPosition,_itemPosition);
                        }
                    });

                    _refreshRestore = false;
                }

                break;
            }
        }
    }

    public boolean isPostIdInAdapter(int postId) {
        if (_adapter != null) {
            int length = _adapter.getCount();
            for (int i = 0; i < length; i++) {
                Post post = _adapter.getItem(i);
                if (post != null && post.getPostId() == postId) {
                    return true;
                }
            }
        }
        return false;
    }

    public void showTaggers(int pos) {
        if (_adapter.getItem(pos) != null && _adapter.getItem(pos).getPostId() > 0) {
            final GetTaggersTask gtt = new GetTaggersTask();

            gtt.execute(_adapter.getItem(pos).getPostId());

            statInc(mMainActivity, "CheckedLOLTaggers");
        }
    }

    public void fastZoop() {
        getListView().post(new Runnable() {
            public void run() {
                getListView().setSelection(getListView().getCount() - 1);
            }
        });
    }

    class GetTaggersTask extends AsyncTask<Integer, Void, CharSequence> {
        protected MaterialDialog mProgressDialog;

        private CharSequence arraylistFormatter(String type, ArrayList<String> arr) {
            if (arr.size() > 0) {
                int color = 0;
                if (type.equals(AppConstants.TAG_TYPE_LOL))
                    color = getResources().getColor(R.color.shacktag_lol);
                if (type.equals(AppConstants.TAG_TYPE_WTF))
                    color = getResources().getColor(R.color.shacktag_wtf);
                if (type.equals(AppConstants.TAG_TYPE_INF))
                    color = getResources().getColor(R.color.shacktag_inf);
                if (type.equals(AppConstants.TAG_TYPE_TAG))
                    color = getResources().getColor(R.color.shacktag_tag);
                if (type.equals(AppConstants.TAG_TYPE_WOW))
                    color = getResources().getColor(R.color.shacktag_wow);
                if (type.equals(AppConstants.TAG_TYPE_AWW))
                    color = getResources().getColor(R.color.shacktag_aww);
                if (type.equals(AppConstants.TAG_TYPE_UNF))
                    color = getResources().getColor(R.color.shacktag_unf);
                SpannableString header = new SpannableString(type + "\'d" + "\n");
                header.setSpan(new ForegroundColorSpan(color), 0, header.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                header.setSpan(new RelativeSizeSpan(1.6f), 0, header.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);

                java.util.Collections.sort(arr, Collator.getInstance());
                ListIterator<String> iter = arr.listIterator();
                String txt = "";
                while (iter.hasNext()) {
                    txt = txt + iter.next() + "\n";
                }

                SpannableString list = new SpannableString(txt);
                list.setSpan(new ForegroundColorSpan(MainActivity.getThemeColor(getActivity(), R.attr.colorUsername)), 0, txt.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);

                return TextUtils.concat(header, list, "\n");
            } else
                return "";
        }

        @Override
        protected CharSequence doInBackground(Integer... params) {

            final Integer parm = params[0];
            mMainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mProgressDialog = MaterialProgressDialog.show(mMainActivity, "Loading Taggers", "Consulting the bones...", true, true, new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface arg0) {
                            cancel(true);
                            System.out.println("CANCELED");
                        }
                    });
                }
            });

            ArrayList<String> resultslol = new ArrayList<String>();
            ArrayList<String> resultsinf = new ArrayList<String>();
            ArrayList<String> resultstag = new ArrayList<String>();
            ArrayList<String> resultsunf = new ArrayList<String>();
            ArrayList<String> resultswtf = new ArrayList<String>();
            ArrayList<String> resultswow = new ArrayList<String>();
            ArrayList<String> resultsaww = new ArrayList<String>();
            try {
                resultslol = ShackApi.getLOLTaggers(parm, AppConstants.TAG_TYPE_LOL);
                resultsinf = ShackApi.getLOLTaggers(parm, AppConstants.TAG_TYPE_INF);
                resultsunf = ShackApi.getLOLTaggers(parm, AppConstants.TAG_TYPE_UNF);
                resultstag = ShackApi.getLOLTaggers(parm, AppConstants.TAG_TYPE_TAG);
                resultswtf = ShackApi.getLOLTaggers(parm, AppConstants.TAG_TYPE_WTF);
                resultswow = ShackApi.getLOLTaggers(parm, AppConstants.TAG_TYPE_WOW);
                resultsaww = ShackApi.getLOLTaggers(parm, AppConstants.TAG_TYPE_AWW);
            } catch (Exception e) {
                e.printStackTrace();
            }

            CharSequence txt = TextUtils.concat(
                    arraylistFormatter(AppConstants.TAG_TYPE_LOL, resultslol),
                    arraylistFormatter(AppConstants.TAG_TYPE_INF, resultsinf),
                    arraylistFormatter(AppConstants.TAG_TYPE_UNF, resultsunf),
                    arraylistFormatter(AppConstants.TAG_TYPE_TAG, resultstag),
                    arraylistFormatter(AppConstants.TAG_TYPE_WTF, resultswtf),
                    arraylistFormatter(AppConstants.TAG_TYPE_WOW, resultswow),
                    arraylistFormatter(AppConstants.TAG_TYPE_AWW, resultsaww)
            );
            return txt;
        }

        @Override
        public void onPostExecute(final CharSequence txt) {
            if (mMainActivity != null) {
                statInc(mMainActivity, "CheckedLOLTaggers");
                mMainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(mMainActivity);
                        builder.setTitle("Taggers for post");
                        builder.setNegativeButton("OK", null);

                        ScrollView scrolly = new ScrollView(mMainActivity);
                        TextView content = new TextView(mMainActivity);
                        content.setPadding(10, 10, 10, 10);
                        content.setTextSize(TypedValue.COMPLEX_UNIT_SP, _adapter._zoom * getResources().getDimension(R.dimen.viewPostTextSize));
                        scrolly.addView(content);
                        content.setText(txt);
                        if (Build.VERSION.SDK_INT >= 11) {
                            content.setTextIsSelectable(true);
                        }
                        System.out.println("SETTING LOLLERS: " + txt);
                        builder.setView(scrolly);
                        AlertDialog alert = builder.create();
                        alert.show();

                        mProgressDialog.dismiss();
                    }
                });
            }
        }
    }

    public void shareURL(int pos, Post p) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, createPostURL(pos));
        if (p != null) {
            String subj = "Chatty post by " + p.getUserName() + " on " + TimeDisplay.getTimeAsMMDDYY(p.getPosted());
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, subj);
        }
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share Post Link"));
    }

    private String createPostURL(int pos) {
        if (_adapter.getItem(pos) != null && _adapter.getItem(pos).getPostId() > 0) {
            String str = AppConstants.SHACKNEWS_CHATTY_URL + "?id=" + _adapter.getItem(pos).getPostId();
            if ((_lastExpanded > 0) && (pos > 0)) {
                str = AppConstants.SHACKNEWS_CHATTY_URL + "?id=" + _adapter.getItem(pos).getPostId();
                str = str + "#item_" + _adapter.getItem(pos).getPostId();
            }
            return str;
        }
        return null;
    }

    public void copyURL(int pos) {
        if (createPostURL(pos) != null) {
            copyString(createPostURL(pos));
        }
    }

    public void copyPostText(int pos) {
        copyString(_adapter.getItem(pos).getCopyText());
    }

    public void copyString(String string) {
        ClipboardManager clipboard = (ClipboardManager) mMainActivity.getSystemService(Activity.CLIPBOARD_SERVICE);
        clipboard.setText(string);
        Toast.makeText(mMainActivity, string, Toast.LENGTH_SHORT).show();
    }

    public void refreshThreadReplies() {
        saveListState();

        if (_adapter != null && _adapter.getCount() > _lastExpanded && _adapter.getItem(_lastExpanded) != null)
            _selectPostIdAfterLoading = _adapter.getItem(_lastExpanded).getPostId();
        _refreshRestore = true;

        System.out.println("LASTEXP REFR" + _lastExpanded + " " + _selectPostIdAfterLoading);
        statInc(mMainActivity, "RefreshedPosts");

        _adapter.clear();
        _adapter.triggerLoadMore();
    }

    public void saveThread() {
        if ((_lastThreadJson != null) && (_adapter != null) && (_adapter.getCount() > 0) && (_adapter.getItem(0) != null)) {
            // false if UNsaved
            if (mMainActivity.mOffline.toggleThread(_adapter.getItem(0).getPostId(), _adapter.getItem(0).getPosted(), _lastThreadJson)) {
                Toast.makeText(mMainActivity, "Thread added to favorites", Toast.LENGTH_SHORT).show();
                _showUnFavSaved = false;
                _showFavSaved = true;
                statInc(mMainActivity, "FavoritedAThread");
            } else {
                Toast.makeText(mMainActivity, "Thread removed from favorites", Toast.LENGTH_SHORT).show();
                _showUnFavSaved = true;
                _showFavSaved = false;
            }
            mMainActivity.updateMenuStarredPostsCount();
            mMainActivity.invalidateOptionsMenu();
            mMainActivity.mRefreshOfflineThreadsWoReplies();
        } else {
            Toast.makeText(mMainActivity, "Error: could not save thread", Toast.LENGTH_SHORT).show();
            System.out.println("TVIEW: no json to save thread with");
        }
    }

    public static final int POST_REPLY = 937;
    public static final int POST_MESSAGE = 947;

    void postReply(final Post parentPost) {
        boolean verified = _prefs.getBoolean("usernameVerified", false);
        if (!verified) {
            LoginForm login = new LoginForm(mMainActivity);
            login.setOnVerifiedListener(new LoginForm.OnVerifiedListener() {
                @Override
                public void onSuccess() {
                    postReply(parentPost);
                }

                @Override
                public void onFailure() {
                }
            });
            return;
        } else if (_messageId == 0) {
            boolean isNewsItem = _adapter.getItem(0).getUserName().equalsIgnoreCase(AppConstants.SHACKNEWS_AUTHOR);
            boolean isCortex = (_adapter.getItem(0).getContent().contains("<br />Read more: <a href=\"" + AppConstants.SHACKNEWS_URL_CORTEX) || _adapter.getItem(0).getContent().contains("<br />Read more: <a href=\"/cortex/"));
            mMainActivity.openComposerForReply(POST_REPLY, parentPost,
                    Integer.parseInt(isNewsItem ? ShackApi.FAKE_NEWS_ID : (isCortex ? ShackApi.FAKE_CORTEX_ID : ShackApi.FAKE_STORY_ID)));
        } else if (_rootPostId == 0) {
            mMainActivity.openComposerForMessageReply(POST_MESSAGE, parentPost, "Re: " + _messageSubject);
        } else {
            ErrorDialog.display(mMainActivity, "Error", "Error determining message TYPE for reply.");
        }
    }

    void toggleNotificationMute(final Post parentPost) {
        boolean verified = _prefs.getBoolean("usernameVerified", false);
        if (!verified) {
            LoginForm login = new LoginForm(mMainActivity);
            login.setOnVerifiedListener(new LoginForm.OnVerifiedListener() {
                @Override
                public void onSuccess() {
                    toggleNotificationMute(parentPost);
                }

                @Override
                public void onFailure() {
                }
            });
            return;
        } else if (_messageId == 0) {
            // do toggle
            boolean isMuted = toggleMuted(parentPost.getPostId(), _prefs);

            Toast.makeText(mMainActivity, (isMuted ? "Reply notifications muted for this post" : "Reply notifications enabled for this post"), Toast.LENGTH_SHORT).show();

            //boolean isNewsItem = _adapter.getItem(0).getUserName().equalsIgnoreCase("shacknews");
            //mMainActivity.openComposerForReply(POST_REPLY, parentPost, isNewsItem);
        } else if (_rootPostId == 0) {
            ErrorDialog.display(mMainActivity, "Error", "Button should not be visible in messages fragment.");
        } else {
            ErrorDialog.display(mMainActivity, "Error", "Error determining message TYPE for mute button.");
        }
    }

    public void shackmessageTo(String username, String subject, String content) {
        mMainActivity.openNewMessagePromptForSubject(username, subject, content);
    }

    public void shackmessageReportPost(String username, int postId) {
        Resources res = getResources();
        String content = String.format(
                res.getString(R.string.moderation_report),
                username,
                AppConstants.SHACKNEWS_CHATTY_URL + "?id=" + postId + System.lineSeparator()
        );
        mMainActivity.openNewMessageForReportingPost(
                res.getString(R.string.moderation_report_to),
                res.getString(R.string.moderation_report_subject),
                content + " "
        );
    }


    private void lolPost(final String tag, final int pos) {
        String userName = _prefs.getString("userName", "");

        boolean verified = _prefs.getBoolean("usernameVerified", false);
        if (!verified) {
            LoginForm login = new LoginForm(mMainActivity);
            login.setOnVerifiedListener(new LoginForm.OnVerifiedListener() {
                @Override
                public void onSuccess() {
                    lolPost(tag, pos);
                }

                @Override
                public void onFailure() {
                }
            });
            return;
        }

        _curTask = new LolTask().execute(userName, tag, Integer.toString(pos));
        if (_adapter != null) {
            _adapter.getItem(pos).setIsWorking(true);
            _adapter.notifyDataSetChanged();
        }
    }

    public void modChoose(final int pos) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mMainActivity);
        builder.setTitle("Shack Moderator Tag");
        final CharSequence[] items = {
                AppConstants.POST_TYPE_INTERESTING,
                AppConstants.POST_TYPE_NWS,
                AppConstants.POST_TYPE_STUPID,
                AppConstants.POST_TYPE_TANGENT,
                AppConstants.POST_TYPE_ONTOPIC,
                AppConstants.POST_TYPE_POLITICAL
        };

        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                modPost((String) items[item], pos);
            }
        });
        AlertDialog alert = builder.create();
        alert.setCanceledOnTouchOutside(true);
        alert.show();
    }

    private void modPost(final String moderation, final int pos) {
        String userName = _prefs.getString("userName", "");
        String password = _prefs.getString("password", "");

        boolean verified = _prefs.getBoolean("usernameVerified", false);
        if (!verified) {
            LoginForm login = new LoginForm(mMainActivity);
            login.setOnVerifiedListener(new LoginForm.OnVerifiedListener() {
                @Override
                public void onSuccess() {
                    modPost(moderation, pos);
                }

                @Override
                public void onFailure() {
                }
            });
            return;
        }

        new ModTask().execute(userName, password, moderation, Integer.toString(pos));
        _progressDialog = MaterialProgressDialog.show(mMainActivity, "Please wait", "Laying down the ban hammer...");
    }

    class LolTask extends AsyncTask<String, Void, Integer> {
        Exception _exception;
        private int pos;
        private int postId;
        private String tag;
        private int response = 0;

        @Override
        protected Integer doInBackground(String... params) {
            String userName = params[0];
            tag = params[1];
            pos = Integer.parseInt(params[2]);
            postId = _adapter.getItem(pos).getPostId();

            try {
                ArrayList<String> userlist = ShackApi.getLOLTaggers(postId, tag);
                if (userlist.contains(userName)) {
                    // user already tagged this. set response to -1 and untag
                    ShackApi.tagPost(postId, tag, userName, true);
                    response = -1;
                } else {
                    response = 1;
                    ShackApi.tagPost(postId, tag, userName, false);
                }
            } catch (Exception ex) {
                Log.e("shackbrowse", "Error tagging post", ex);
                _exception = ex;
            }

            return response;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (_adapter != null && _adapter.getCount() >= pos && _adapter.getItem(pos) != null && _adapter.getItem(pos).getPostId() == postId) {
                LolObj updLol = _adapter.getItem(pos).getLolObj();
                if (updLol == null)
                    updLol = new LolObj();

                if (response == 1) {
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_LOL)) {
                        updLol.incLol();
                        statInc(mMainActivity, "GaveALOLTag");
                        statInc(mMainActivity, "GaveALOLTaglol");
                    }
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_TAG)) {
                        updLol.incTag();
                        statInc(mMainActivity, "GaveALOLTag");
                        statInc(mMainActivity, "GaveALOLTagtag");
                    }
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_WOW)) {
                        updLol.incWow();
                        statInc(mMainActivity, "GaveALOLTag");
                        statInc(mMainActivity, "GaveALOLTagwow");
                    }
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_AWW)) {
                        updLol.incAww();
                        statInc(mMainActivity, "GaveALOLTag");
                        statInc(mMainActivity, "GaveALOLTagaww");
                    }
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_WTF)) {
                        updLol.incWtf();
                        statInc(mMainActivity, "GaveALOLTag");
                        statInc(mMainActivity, "GaveALOLTagwtf");
                    }
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_INF)) {
                        updLol.incInf();
                        statInc(mMainActivity, "GaveALOLTag");
                        statInc(mMainActivity, "GaveALOLTaginf");
                    }
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_UNF)) {
                        updLol.incUnf();
                        statInc(mMainActivity, "GaveALOLTag");
                        statInc(mMainActivity, "GaveALOLTagunf");
                    }
                }
                if (response == -1) {
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_LOL)) {
                        updLol.decLol();
                        statInc(mMainActivity, "RemovedALOLTag");
                        statInc(mMainActivity, "RemovedALOLTaglol");
                    }
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_TAG)) {
                        updLol.decTag();
                        statInc(mMainActivity, "RemovedALOLTag");
                        statInc(mMainActivity, "RemovedALOLTagtag");
                    }
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_WOW)) {
                        updLol.decWow();
                        statInc(mMainActivity, "RemovedALOLTag");
                        statInc(mMainActivity, "RemovedALOLTagwow");
                    }
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_AWW)) {
                        updLol.decAww();
                        statInc(mMainActivity, "RemovedALOLTag");
                        statInc(mMainActivity, "RemovedALOLTagaww");
                    }
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_WTF)) {
                        updLol.decWtf();
                        statInc(mMainActivity, "RemovedALOLTag");
                        statInc(mMainActivity, "RemovedALOLTagwtf");
                    }
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_INF)) {
                        updLol.decInf();
                        statInc(mMainActivity, "RemovedALOLTag");
                        statInc(mMainActivity, "RemovedALOLTaginf");
                    }
                    if (tag.equalsIgnoreCase(AppConstants.TAG_TYPE_UNF)) {
                        updLol.decUnf();
                        statInc(mMainActivity, "RemovedALOLTag");
                        statInc(mMainActivity, "RemovedALOLTagunf");
                    }
                }

                updLol.genTagSpan(mMainActivity);
                _adapter.getItem(pos).setLolObj(updLol);
                _adapter.getItem(pos).setIsWorking(false);
                _adapter.notifyDataSetChanged();
            }
            if (_exception != null)
                ErrorDialog.display(mMainActivity, "Error", "Error tagging post:\n" + _exception.getMessage());
        }
    }

    class ModTask extends AsyncTask<String, Void, String> {
        Exception _exception;

        @Override
        protected String doInBackground(String... params) {
            String userName = params[0];
            String password = params[1];
            String moderation = params[2];
            int pos = Integer.parseInt(params[3]);

            try {
                int rootPost = _adapter.getItem(0).getPostId();
                String result = ShackApi.modPost(userName, password, rootPost, _adapter.getItem(pos).getPostId(), moderation);
                return result;
            } catch (Exception e) {
                _exception = e;
                Log.e("shackbrowse", "Error modding post", e);
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            _progressDialog.dismiss();
            if (_exception != null)
                ErrorDialog.display(mMainActivity, "Error", "Error occured modding post.");
            else if (result != null)
                ErrorDialog.display(mMainActivity, "Moderation", result);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // returning from compose post view
            case POST_REPLY:
                if (resultCode == Activity.RESULT_OK) {
                    // read the resulting thread id from the post
                    // this is either the id of your new post or the id of the post your replied to
                    int PQPId = (int) data.getExtras().getLong("PQPId");
                    int parentPostId = data.getExtras().getInt("parentPostId");
                    _selectPostIdAfterLoading = PQPId;
                    _isSelectPostIdAfterLoadingIdaPQPId = true;

                    if (_adapter != null) {
                        _adapter.fakePostRemoveinator();
                        _adapter.fakePostAddinator(_adapter.getAll());
                        _adapter.createThreadTree(_adapter.getAll());
                        _adapter.notifyDataSetChanged();
                        ensurePostSelectedAndDisplayed();
                    }
                    //System.out.println("RECV PR: " + _selectPostIdAfterLoading);
                    // _rootPostId = parentPostId; // should cause the thread to load, then _selectpqpid will select the fakepost
                    //_adapter.clear();
                    //_adapter.triggerLoadMore();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // displayPost(v);
    }

    private ExpandableListItemAdapter.titleViewOnClick getTitleViewOnClickListener() {
        return new ExpandableListItemAdapter.titleViewOnClick() {
            @Override
            public void OnClick(View contentParent) {
                // do not collapse root posts
                if (_adapter.findPositionForId((Long) contentParent.getTag()) == 0)
                    return;

                statInc(mMainActivity, "OpenedPost");

                if (_adapter.mAnimSpeed == 0f) {
                    // no animation
                    displayPost((View) contentParent.getParent());
                } else {
                    // animations
                    _lastExpanded = _adapter.findPositionForId((Long) contentParent.getTag());
                    _adapter.toggle(contentParent);
                }
            }
        };
    }


    // the following function is a remnant from olden times. it is only used when animations are turned off.
    private void displayPost(View v) {
        if (getListView().getPositionForView(v) != ListView.INVALID_POSITION) {
            this._postYLoc = v.getTop();
            expandAndCheckPostWithoutAnimation(v);

            // calculate sizes
            Display display = ((WindowManager) v.getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            DisplayMetrics displaymetrics = new DisplayMetrics();
            display.getMetrics(displaymetrics);

            // move things around to prevent weird scrolls
            getListView().setSelectionFromTop(getListView().getPositionForView(v), _postYLoc - (_userNameHeight + (int) (30 * (displaymetrics.ydpi / 160))));

            // keep the child view on screen
            final View view = v;
            final int pos = getListView().getPositionForView(view);
            final ListView listView = getListView();
            listView.post(new Runnable() {

                @Override
                public void run() {
                    View betterView = getListViewChildAtPosition(pos, listView);
                    if (betterView != null) {
                        listView.requestChildRectangleOnScreen(betterView, new Rect(0, 0, betterView.getRight(), betterView.getHeight()), false);
                    } else {
                        listView.requestChildRectangleOnScreen(view, new Rect(0, 0, view.getRight(), view.getHeight()), false);
                    }
                }
            });
        }
    }

    private View getListViewChildAtPosition(int position, ListView listView) {
        int wantedPosition = position; // Whatever position you're looking for
        int firstPosition = listView.getFirstVisiblePosition() - listView.getHeaderViewsCount(); // This is the same as child #0
        int wantedChild = wantedPosition - firstPosition;
        // Say, first visible position is 8, you want position 10, wantedChild will now be 2
        // So that means your view is child #2 in the ViewGroup:
        if (wantedChild < 0 || wantedChild >= listView.getChildCount()) {
            Log.w("SB3", "Unable to get view for desired position, because it's not being displayed on screen.");
            return null;
        }
        // Could also check if wantedPosition is between listView.getFirstVisiblePosition() and listView.getLastVisiblePosition() instead.
        View wantedView = listView.getChildAt(wantedChild);
        return wantedView;
    }

    private void expandAndCheckPostWithoutAnimation(View v) {
        expandAndCheckPostWithoutAnimation(getListView().getPositionForView(v));
    }

    private void expandAndCheckPostWithoutAnimation(int listviewposition) {
        _adapter.expandWithoutAnimation(listviewposition);
        System.out.println("EXPANDING " + listviewposition);
    	/*
    	Post post = null;

    	int adapterposition = listviewposition;

    	// sanity check
    	if (adapterposition >= 0 && adapterposition < _adapter.getCount())
        {
    		post = _adapter.getItem(adapterposition);
        }

        // user clicked "Loading..."
        if (post == null)
            return;

        getListView().setItemChecked(listviewposition, true);

        // never unexpand the root post
        if ((_lastExpanded > 0) && (_lastExpanded <= (_adapter.getCount())) && (_lastExpanded != listviewposition))
        {
        	Post oldpost = _adapter.getItem(this._lastExpanded);
        	oldpost.setExpanded(false);
        	getListView().setItemChecked(_lastExpanded, false);

        	// scroll helper
        }
        */
        _lastExpanded = listviewposition;
        /*
        post.setExpanded(true);

       	_adapter.notifyDataSetChanged();
       	*/
    }

    private void expandAndCheckPost(int listviewposition) {
        _adapter.expand(listviewposition);
        _lastExpanded = listviewposition;
    }

    class PostLoadingAdapter extends ExpandableLoadingAdapter<Post> {
        boolean _lolsInPost = true;
        boolean _getLols = (true && MainActivity.LOLENABLED);
        float _zoom = 1.0f;
        int _maxWidth = 400;
        int _bulletWidth = 20;
        int _maxBullets = 8;
        String _donatorList = "";
        private String _donatorGoldList = "";
        private String _donatorQuadList = "";
        boolean _replyNotificationsEnabled;
        boolean _showModTools = false;
        private boolean mAnonMode = false;
        private boolean mViewIsOpen = true;
        private boolean _showHoursSince = true;
        private boolean _hideLinks = true;
        private int _embedImages = 2;
        private int _embedVideos = 1;
        private boolean _linkButtons = true;
        private boolean mEchoPalatize = false;
        private boolean mEchoEnabled = false;
        private boolean mAutoEchoEnabled = false;
        private String _userName = "";
        private boolean _verified = false;
        private String _OPuserName = "";
        private HashMap<String, HashMap<String, LolObj>> _shackloldata = new HashMap<String, HashMap<String, LolObj>>();

        private Bitmap _bulletBlank;
        private Bitmap _bulletSpacer;
        private Bitmap _bulletEnd;
        private Bitmap _bulletExtendPast;
        private Bitmap _bulletBranch;
        private Bitmap _bulletCollapse;
        private BitmapDrawable _donatorIcon;
        private BitmapDrawable _donatorGoldIcon;
        private BitmapDrawable _donatorQuadIcon;
        private BitmapDrawable _briefcaseIcon;
        private boolean _displayLimes = true;
        private HashMap<String, LolObj> _threadloldata;
        private Bitmap _bulletEndNew;
        private Bitmap _bulletExtendPastNew;
        private Bitmap _bulletBranchNew;
        private LruCache<String, Bitmap> mMemoryCache;
        private boolean _fastScroll = false;
        private NetworkInfo mWifi;
        private ArrayList<ExoPlayerTracker> mExoPlayers = new ArrayList<ExoPlayerTracker>();
        private String _fusers = "";

        private class ExoPlayerTracker {
            PlayerView mPlayerView;
            ExoPlayer mPlayer;
            int mPosition;
            String mTag;

            ExoPlayerTracker(int position, PlayerView playerView, ExoPlayer player, String tag) {
                mPlayer = player;
                mPlayerView = playerView;
                mPosition = position;
                mTag = tag;
            }
        }

        // -1 clears all players
        public void exoPlayerCleanup(int position) {
            Iterator it = mExoPlayers.iterator();
            while (it.hasNext()) {
                ExoPlayerTracker item = (ExoPlayerTracker) it.next();

                if ((item.mPosition == position) || (position == -1)) {
                    item.mPlayerView.getVideoSurfaceView().setVisibility(View.GONE);
                    item.mPlayerView.setVisibility(View.GONE);
                    item.mPlayer.stop();
                    item.mPlayer.clearVideoSurface();
                    item.mPlayer.release();
                    item.mPlayer = null;
                    item.mPlayerView.setPlayer(null);
                    item.mPlayerView = null;
                    it.remove();
                }
            }
        }

        public void exoPlayerPause() {
            Iterator it = mExoPlayers.iterator();
            while (it.hasNext()) {
                ExoPlayerTracker item = (ExoPlayerTracker) it.next();
                item.mPlayer.setPlayWhenReady(false);
            }
        }

        public void setViewIsOpened(boolean isOpened) {
            if (isOpened) {
                mViewIsOpen = true;
                setHoldPostExecute(false);
            } else {
                if (((MainActivity) getActivity()).getDualPane()) {
                    mViewIsOpen = true;
                    setHoldPostExecute(false);
                } else {
                    mViewIsOpen = false;
                    exoPlayerPause();
                    setHoldPostExecute(true);
                }
            }
        }

        public ExpandCollapseListener mExpandCollapseListener = new ExpandCollapseListener() {
            @Override
            public void onItemExpanded(int i) {
                View v = _adapter.getTitleView(i);
                if (v != null) {
                    TextView userName = (TextView) v.findViewById(R.id.textPreviewUserName);
                    userName.setOnClickListener(_adapter.getUserNameClickListenerForPosition(i, v));
                    userName.setClickable(true);
                }
            }

            @Override
            public void onItemCollapsed(int i) {
                // this releases the embed exoplayer for .mp4 to prevent memory leaks
                exoPlayerCleanup(i);

                View v = _adapter.getTitleParent(i);
                if (v != null) {
                    TextView userName = (TextView) v.findViewById(R.id.textPreviewUserName);
                    userName.setOnClickListener(null);
                    userName.setClickable(false);
                }
            }
        };


        public View.OnClickListener getUserNameClickListenerForPosition(int pos, View v) {
            final int postId = getItem(pos).getPostId();
            final String unamefinal = getItem(pos).getUserName();
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PopupMenu usrpop = new PopupMenu(getContext(), v);
                    usrpop.getMenu().add(Menu.NONE, 0, Menu.NONE, "Shack Message " + unamefinal);
                    usrpop.getMenu().add(Menu.NONE, 1, Menu.NONE, "Search for posts by " + unamefinal);
                    usrpop.getMenu().add(Menu.NONE, 2, Menu.NONE, "Highlight " + unamefinal + " in thread");
                    usrpop.getMenu().add(Menu.NONE, 3, Menu.NONE, "Copy " + unamefinal + " to clipboard");

                    usrpop.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            switch (item.getItemId()) {
                                case 0:
                                    shackmessageTo(unamefinal, null, null);
                                    break;
                                case 1:
                                    searchForPosts(unamefinal);
                                    break;
                                case 2:
                                    mMainActivity.openHighlighter(unamefinal);
                                    break;
                                case 3:
                                    copyString(unamefinal);
                                    break;
                                case 4:
                                    shackmessageReportPost(unamefinal, postId);
                                    break;
                                case 5:
                                    mMainActivity.blockUser(unamefinal);
                                    break;
                            }
                            return true;
                        }
                    });
                    usrpop.show();
                }
            };
        }

        public PostLoadingAdapter(Context context, ArrayList<Post> items) {
            super(context, items);
            loadPrefs();

            if (((MainActivity) getActivity()).getDualPane()) {
                setViewIsOpened(true);
            } else {
                setHoldPostExecute(true);
            }

            // Get max available VM memory, exceeding this amount will throw an
            // OutOfMemory exception. Stored in kilobytes as LruCache takes an
            // int in its constructor.
            final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

            // Use 1/3th of the available memory for this memory cache.
            final int cacheSize = maxMemory / 3;

            mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    // The cache size will be measured in kilobytes rather than
                    // number of items.

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
                        return ((bitmap.getRowBytes() * bitmap.getHeight()) / 1024) / 1024;
                    } else {
                        return bitmap.getByteCount() / 1024;
                    }
                }
            };

        }

        public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
            if ((bitmap != null) && (getBitmapFromMemCache(key) == null)) {
                mMemoryCache.put(key, bitmap);
            }
        }

        public Bitmap getBitmapFromMemCache(String key) {
            return mMemoryCache.get(key);
        }

        public void clearBitmapMemCache() {
            mMemoryCache.evictAll();
        }

        @Override
        public void clear() {
            // reload preferences
            loadPrefs();

            // not used now that memcache is based on depthstring keys
            clearBitmapMemCache();


            _lastExpanded = 0;

            // clear any errant checkeds
            for (int i = 0; i < this.getCount(); i++) {
                getListView().setItemChecked(i, false);
            }

            // this releases the embed exoplayer for .mp4 to prevent memory leaks
            exoPlayerCleanup(-1);

            super.clear();
        }

        void loadPrefs() {
            mEchoPalatize = _prefs.getBoolean("echoPalatize", false);
            mEchoEnabled = _prefs.getBoolean("echoEnabled", false);
            mAutoEchoEnabled = (_prefs.getBoolean("echoChamberAuto", true) && mEchoEnabled);
            mAnonMode = _prefs.getBoolean("donkeyanonoption", false);
            _userName = _prefs.getString("userName", "").trim();
            _verified = _prefs.getBoolean("usernameVerified", false);
            _lolsInPost = _prefs.getBoolean("showPostLolsThreadView", true);
            _getLols = (_prefs.getBoolean("getLols", true) && MainActivity.LOLENABLED);
            _zoom = Float.parseFloat(_prefs.getString("fontZoom", "1.0"));
            _showModTools = _prefs.getBoolean("showModTools", false);
            _showHoursSince = _prefs.getBoolean("showHoursSince", true);
            _hideLinks = _prefs.getBoolean("hideLinksWhenEmbed", false);
            _embedImages = Integer.parseInt(_prefs.getString("embedImages", "2"));
            _embedVideos = Integer.parseInt(_prefs.getString("embedVideos", "1"));
            _linkButtons = _prefs.getBoolean("showLinkOptionsButton", true);
            _fastScroll = _prefs.getBoolean("speedyScroll", false);
            _donatorList = _prefs.getString("limeUsers", "");
            _donatorGoldList = _prefs.getString("goldLimeUsers", "");
            _donatorQuadList = _prefs.getString("quadLimeUsers", "");
            _displayLimes = _prefs.getBoolean("displayLimes", false);
            _replyNotificationsEnabled = (_prefs.getBoolean("noteReplies", false) && _prefs.getBoolean("noteEnabled", false));

            setupPref();


            // fast scroll on mega threads
            if (mMainActivity != null) {

                if (MainActivity.getThemeId(getActivity()) == R.style.AppThemeWhite) {
                    _displayLimes = false;
                }

                boolean set = false;
                if ((getCount() > 300))
                    set = true;
                final boolean set2 = set && _fastScroll;

                mMainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (_viewAvailable) {
                            if (getListView() != null) {
                                getListView().setFastScrollEnabled(set2);
                            }
                        }
                    }
                });
            }

            // check for wifi connection
            ConnectivityManager connManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            // calculate sizes for deep threads
            Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            DisplayMetrics displaymetrics = new DisplayMetrics();
            display.getMetrics(displaymetrics);
            _maxWidth = (int) (displaymetrics.widthPixels * (.7));

            _maxBullets = (int) Math.floor(_maxWidth / _bulletWidth);
            // failsafe
            if (_maxBullets < 8) _maxBullets = 8;

            createAllBullets();
        }

        @Override
        protected void setCurrentlyLoading(final boolean set) {
            super.setCurrentlyLoading(set);

            if (mMainActivity != null) {
                mMainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (_viewAvailable) {
                            updateThreadViewUi();

                            if (set)
                                ((MainActivity) getActivity()).startProgressBar();
                            else
                                ((MainActivity) getActivity()).stopProgressBar();

                            // if (ptrLayout != null)
                            // 	ptrLayout.setRefreshing(true);
                        }
                    }
                });
            }
        }


        @Override
        public View getContentView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mMainActivity).inflate(R.layout.thread_row_expanded, parent, false);
            }

            return createView(position, convertView, parent, true);
        }

        @Override
        public View getTitleView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mMainActivity).inflate(R.layout.thread_row_preview, parent, false);
            }

            return createView(position, convertView, parent, false);
        }

        @Override
        public void loadExpandedViewDataIntoView(int position, View convertView) {
            if (convertView != null && convertView.getTag() != null) {
                ViewHolder holder = (ViewHolder) convertView.getTag();
                final Post p = getItem(position);

                // load expanded data

                holder.expandedView.setBackgroundColor(MainActivity.getThemeColor(getActivity(), R.attr.colorSelHighPostBG));

                // set lol tags
                if (p.getLolObj() != null) {
                    holder.expLolCounts.setText(p.getLolObj().getTagSpan());
                } else {
                    holder.expLolCounts.setText("");
                }

                Spannable postTextContent = (Spannable) p.getFormattedContent();

                final int pos = position;

                holder.buttonReply.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        postReply(p);
                    }
                });

                final ImageButton buttonNoteEnabled = holder.buttonNoteEnabled;
                final ImageButton buttonNoteMuted = holder.buttonNoteMuted;
                holder.buttonNoteEnabled.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleNotificationMute(p);
                        buttonNoteEnabled.setVisibility(View.GONE);
                        buttonNoteMuted.setVisibility(View.VISIBLE);
                    }
                });
                holder.buttonNoteMuted.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleNotificationMute(p);
                        buttonNoteEnabled.setVisibility(View.VISIBLE);
                        buttonNoteMuted.setVisibility(View.GONE);
                    }
                });

                boolean mutedButtonsVisible = (p.getUserName().equalsIgnoreCase(_userName) && (_messageId == 0) && (_replyNotificationsEnabled) && !p.isPQP());
                boolean mutedPost = checkIfMuted(p.getPostId(), _prefs);
                holder.buttonNoteMuted.setVisibility(mutedPost && mutedButtonsVisible ? View.VISIBLE : View.GONE);
                holder.buttonNoteEnabled.setVisibility(!mutedPost && mutedButtonsVisible ? View.VISIBLE : View.GONE);

                final ImageButton btnlol = holder.buttonLol;
                holder.buttonLol.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopupMenu lolpop = new PopupMenu(getContext(), btnlol);
                        lolpop.getMenu().add(Menu.NONE, 6, Menu.NONE, "Who Tagged?");
                        lolpop.getMenu().add(Menu.NONE, 0, Menu.NONE, AppConstants.TAG_TYPE_LOL);
                        lolpop.getMenu().add(Menu.NONE, 1, Menu.NONE, AppConstants.TAG_TYPE_INF);
                        lolpop.getMenu().add(Menu.NONE, 2, Menu.NONE, AppConstants.TAG_TYPE_UNF);
                        SubMenu sub = lolpop.getMenu().addSubMenu(Menu.NONE, 3, Menu.NONE, "More...");
                        sub.add(Menu.NONE, 4, Menu.NONE, AppConstants.TAG_TYPE_WOW);
                        sub.add(Menu.NONE, 5, Menu.NONE, AppConstants.TAG_TYPE_WTF);
                        sub.add(Menu.NONE, 8, Menu.NONE, AppConstants.TAG_TYPE_TAG);
                        sub.add(Menu.NONE, 7, Menu.NONE, AppConstants.TAG_TYPE_AWW);
                        lolpop.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                if (item.getItemId() == 3)
                                    return false;
                                if (item.getItemId() == 6)
                                    new GetTaggersTask().execute(_adapter.getItem(pos).getPostId());
                                else
                                    lolPost((String) item.getTitle(), pos);
                                return true;
                            }
                        });
                        lolpop.show();
                    }
                });

                // open all images button
                final CustomURLSpan[] urlSpans = ((SpannableString) postTextContent).getSpans(0, postTextContent.length(), CustomURLSpan.class);
                if (urlSpans.length > 1) {
                    String _href;
                    ArrayList<String> hrefs = new ArrayList<String>();
                    for (int i = 0; i < urlSpans.length; i++) {
                        _href = urlSpans[i].getURL().trim();
                        if (PopupBrowserFragment.isImage(_href)) {
                            hrefs.add(_href);
                        }
                    }
                    String[] hrefs2 = new String[hrefs.size()];
                    hrefs.toArray(hrefs2);
                    final String[] hrefs3 = hrefs2;
                    holder.buttonAllImages.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mMainActivity.openBrowser(hrefs3);
                        }
                    });
                    if (hrefs2.length > 1)
                        holder.buttonAllImages.setVisibility(View.VISIBLE);
                    else
                        holder.buttonAllImages.setVisibility(View.GONE);

                } else {
                    holder.buttonAllImages.setVisibility(View.GONE);
                }

                final ImageButton butBlocks = holder.buttonBlocks;
                holder.buttonBlocks.setOnClickListener(new View.OnClickListener() {
                    final int postId = getItem(pos).getPostId();
                    final String unamefinal = getItem(pos).getUserName();

                    @Override
                    public void onClick(View view) {
                        PopupMenu blocksPopup = new PopupMenu(getContext(), butBlocks);
                        blocksPopup.getMenu().add(Menu.NONE, 0, Menu.NONE, "Report this user/post");
                        if (!unamefinal.equalsIgnoreCase(_userName)) {
                            blocksPopup.getMenu().add(Menu.NONE, 1, Menu.NONE, "Block this user");
                        }
                        blocksPopup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                switch (item.getItemId()) {
                                    case 0:
                                        shackmessageReportPost(unamefinal, postId);
                                        break;
                                    case 1:
                                        mMainActivity.blockUser(unamefinal);
                                        break;
                                }
                                return true;
                            }
                        });
                        blocksPopup.show();
                    }
                });


                final ImageButton butshr = holder.buttonSharePost;
                holder.buttonSharePost.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        PopupMenu sharepop = new PopupMenu(getContext(), butshr);
                        sharepop.getMenu().add(Menu.NONE, 0, Menu.NONE, "Copy Post Text");
                        if (_messageId == 0) {
                            // not a message
                            sharepop.getMenu().add(Menu.NONE, 1, Menu.NONE, "Copy URL of Post");
                            sharepop.getMenu().add(Menu.NONE, 2, Menu.NONE, "Share Link to Post");
                        }
                        sharepop.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                switch (item.getItemId()) {
                                    case 0:
                                        copyPostText(pos);
                                        break;
                                    case 1:
                                        copyURL(pos);
                                        break;
                                    case 2:
                                        shareURL(pos, p);
                                        break;
                                }
                                return true;
                            }
                        });
                        sharepop.show();
                    }
                });


                final ImageButton btnothr = holder.buttonOther;
                holder.buttonOther.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        modChoose(pos);
                    }
                });


                // this must be done for recycled views, setmodtagsfalse doesnt handle loading
                if (!_adapter.isCurrentlyLoading()) {
                    //holder.rowtype.setLoading(false);
                    holder.loading.setVisibility(View.GONE);
                }

                // check if root post and loading
                if (((p.getPostId() == _rootPostId) && (_adapter.isCurrentlyLoading()))) {
                    holder.expandedView2.setVisibility(View.VISIBLE);
                    holder.loading.setVisibility(View.VISIBLE);
                    //holder.rowtype.setLoading(true);
                } else {
                    holder.expandedView2.setVisibility(View.GONE);
                    holder.loading.setVisibility(View.GONE);
                    //holder.rowtype.setLoading(false);
                }


                // hide buttons on pqp posts
                if (p.isPQP()) {
                    holder.buttonSharePost.setVisibility(View.GONE);
                    holder.buttonReply.setVisibility(View.GONE);
                    //holder.rowtype.setLoading(false);
                } else {
                    holder.buttonSharePost.setVisibility(View.VISIBLE);
                    holder.buttonReply.setVisibility(View.VISIBLE);
                }
                // lol button

                holder.buttonOther.setVisibility(View.GONE);
                if ((_messageId == 0) && (!p.isPQP())) {
                    // is a real post in a thread, not queued or shack message
                    if (MainActivity.LOLENABLED) {
                        holder.buttonLol.setVisibility(View.VISIBLE);
                    } else {
                        holder.buttonLol.setVisibility(View.GONE);
                    }

                    if ((_showModTools) && (_rootPostId != 0)) {
                        holder.buttonOther.setVisibility(View.VISIBLE);
                    }
                } else {
                    holder.buttonLol.setVisibility(View.GONE);
                }


                // dont bother recreating views
                holder.postContent.removeAllViews();

                boolean doEmbedItemsImages = (_embedImages == 1 && mWifi.isConnected()) || _embedImages == 2;
                boolean doEmbedItemsVideos = (_embedVideos == 1 && mWifi.isConnected()) || _embedVideos == 2;
                boolean removeLinksImages = (_hideLinks && doEmbedItemsImages);
                boolean removeLinksVideos = (_hideLinks && doEmbedItemsVideos);

                ArrayList<PostClip> choppedPost;
                // try to load saved data
                if (getItem(position).getChoppedPost() != null) {
                    choppedPost = getItem(position).getChoppedPost();

                    System.out.println("POSTCHOP: USED CACHE");
                } else {
                    choppedPost = postTextChopper(postTextContent, removeLinksImages, removeLinksVideos);
                    getItem(position).setChoppedPost(choppedPost);
                    System.out.println("POSTCHOP: MADE NEW");
                }


                for (int i = choppedPost.size() - 1; i >= 0; i--) {
                    PostClip postClip = choppedPost.get(i);
                    if (postClip.text.toString().trim().length() > 0) {
                        FixedTextView postText = new FixedTextView(getContext());
                        Spannable postClipText = postClip.text;

                        if (_linkButtons && !((postClip.type == PostClip.TYPE_IMAGE) && (postClip.url != null) && (doEmbedItemsImages)))
                            postClipText = (Spannable) applyExtLink(postClipText, postText);

                        postText.setTextColor(MainActivity.getThemeColor(getActivity(), R.attr.colorText));
                        // postText.setTextColor(getResources().getColor(R.color.nonpreview_post_text_color));
                        // debug
                        //Random color = new Random();
                        //postText.setBackgroundColor(Color.argb(255, color.nextInt(255), color.nextInt(255), color.nextInt(255)));
                        postText.setText(applyHighlight(postClipText), BufferType.SPANNABLE);
                        postText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        postText.setTextSize(TypedValue.COMPLEX_UNIT_PX, postText.getTextSize() * _zoom);

                        // links stuff
                        postText.setLinkTextColor(MainActivity.getThemeColor(getActivity(), R.attr.colorLink));
                        postText.setTextIsSelectable(true);
                        postText.setFocusable(true);
                        postText.setFocusableInTouchMode(true);
                        postText.setMovementMethod(new CustomLinkMovementMethod());
                        StyleCallback cb = new StyleCallback();
                        cb.setTextView(postText);
                        postText.setCustomSelectionActionModeCallback(cb);


                        holder.postContent.addView(postText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    }
                    // embed images test

                    Display display = mMainActivity.getWindowManager().getDefaultDisplay();
                    Point size = new Point();
                    display.getSize(size);
                    int width = Math.round(size.x * 1.0f);
                    if (mMainActivity.getDualPane()) {
                        width = Math.round((2f / 3f) * width);
                    }

                    // EMBED IMAGES
                    if (((postClip.type == PostClip.TYPE_IMAGE) && (postClip.url != null) && (doEmbedItemsImages))
                            && (PopupBrowserFragment.isImage(postClip.url.getURL(), false) || ((PopupBrowserFragment.isImage(postClip.url.getURL(), true)) && doEmbedItemsVideos))) {
                        ImageView image = new ImageView(getContext());
                        final String url = postClip.url.getURL().trim();

                        System.out.println("EMBED:" + PopupBrowserFragment.imageUrlFixer(url));

                        image.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(v.getContext());
                                statInc(v.getContext(), "ClickedLink");
                                int _useBrowser = Integer.parseInt(prefs.getString("usePopupBrowser2", "1"));
                                if (_useBrowser != 3)
                                    mMainActivity.openBrowser(url);
                                else {
                                    Uri u = Uri.parse(url);
                                    if (u.getScheme() == null) {
                                        u = Uri.parse("http://" + url);
                                    }
                                    Intent i = new Intent(Intent.ACTION_VIEW, u);
                                    v.getContext().startActivity(i);
                                }
                            }
                        });
                        image.setOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                showLinkOptionsMenu(url);
                                return false;
                            }
                        });
                        //Random ncolor = new Random();
                        //image.setBackgroundColor(Color.argb(255, ncolor.nextInt(255), ncolor.nextInt(255), ncolor.nextInt(255)));
                        holder.postContent.addView(image, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                        Glide.with(mMainActivity)
                                .load(PopupBrowserFragment.imageUrlFixer(url))
                                .apply(new RequestOptions()
                                        .override(width, width)
                                        .fitCenter()
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        .placeholder(R.drawable.ic_action_image_photo)
                                        .error(R.drawable.ic_action_content_flag))
                                .into(image);
                    }
                    // YOUTUBE CRAP
                    if ((postClip.type == PostClip.TYPE_YOUTUBE) && (postClip.url != null) && (doEmbedItemsImages)) {

                        ImageView playButton = new ImageView(getContext());

                        playButton.setImageResource(R.drawable.youtube_open);

                        playButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                mMainActivity.openYoutube(postClip.url.getURL().trim());
                            }
                        });

                        holder.postContent.addView(playButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                        System.out.println("EMBEDY:" + PopupBrowserFragment.imageUrlFixer(postClip.url.getURL()));

                    }
                    // MP4 CRAP
                    if ((postClip.type == PostClip.TYPE_MP4) && (postClip.url != null) && (doEmbedItemsVideos)) {

                        // check if player already exists, recycle view if it does
                        boolean recycle = false;
                        for (int j = 0; j < mExoPlayers.size(); j++) {
                            ExoPlayerTracker item = mExoPlayers.get(j);

                            if ((item.mPosition == position) && (item.mTag == postClip.url.getURL())) {
                                if (item.mPlayerView.getParent() != null)
                                    ((ViewGroup) item.mPlayerView.getParent()).removeView(item.mPlayerView);
                                holder.postContent.addView(item.mPlayerView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                                recycle = true;
                            }
                        }

                        // couldnt recycle view, so make a new one
                        if (!recycle) {
                            final PlayerView view = new PlayerView(getContext());
                            // Create the player
                            ExoPlayer player = new ExoPlayer.Builder(getContext()).build();
                            view.setPlayer(player);

                            // track player items to avoid memory leaks
                            ExoPlayerTracker trackItem = new ExoPlayerTracker(position, view, player, postClip.url.getURL());
                            mExoPlayers.add(trackItem);

                            // This is the MediaItem representing the media to be played.
                            Uri clipUri = Uri.parse(PopupBrowserFragment.getGIFVtoMP4(postClip.url.getURL()));
                            MediaItem mediaItem = MediaItem.fromUri(clipUri);
                            player.setMediaItem(mediaItem);
                            boolean playWhenReady = position == 0 || mViewIsOpen;
                            player.setPlayWhenReady(playWhenReady);
                            player.setRepeatMode(Player.REPEAT_MODE_ONE);
                            view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
                            view.setControllerAutoShow(false);
                            player.addListener(new Player.Listener() {
                                @Override
                                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                                    if (playbackState == Player.STATE_READY) {
                                        view.hideController();

                                        // no longer loading, set tag
                                        Integer i = 0;
                                        view.setTag(i);
                                    }
                                    System.out.println("VIDonplayerstatecha" + playbackState + playWhenReady);
                                }

                                @Override
                                public void onPositionDiscontinuity(int reason) {
                                    // this is the restart from a repeating video
                                    // view tag just happens to be a place i can store data
                                    Integer i = (Integer) view.getTag() + 1;
                                    // this causes the video to repeat a total of 3 times total before stopping
                                    // this is only needed because some dummy left his phone on a long video overnight on cell data and SB used 20 gigs repeating the video
                                    if (i > 1) {
                                        player.setRepeatMode(Player.REPEAT_MODE_OFF);
                                        System.out.println("STOPPED VIDEO REPEAT");
                                    }
                                    view.setTag(i);
                                }
                            });
                            player.prepare();
                            holder.postContent.addView(view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                        }

                    }
                }
            }
        }

        protected View createView(int position, View convertView, ViewGroup parent, boolean isExpanded) {
            // get the thread to display and populate all the data into the layout
            Post p = getItem(position);

            ViewHolder holder = (ViewHolder) convertView.getTag();
            if ((holder == null) && (!isExpanded)) {
                holder = new ViewHolder();

                // preview items
                holder.previewView = (CheckableTableLayout) convertView.findViewById(R.id.previewView);
                holder.previewRow = (TableRow) convertView.findViewById(R.id.previewRow);

                holder.treeIcon = (ImageView) convertView.findViewById(R.id.treeIcon);
                holder.postingThrobber = (ProgressBar) convertView.findViewById(R.id.postingThrobber);

                holder.preview = (TextView) convertView.findViewById(R.id.textPreview);
                holder.previewLolCounts = (TextView) convertView.findViewById(R.id.textPostLolCounts);
                holder.previewUsernameHolder = (LinearLayout) convertView.findViewById(R.id.previewUNHolder);
                holder.previewUsername = (TextView) convertView.findViewById(R.id.textPreviewUserName);
                holder.previewLimeHolder = (ImageView) convertView.findViewById(R.id.previewLimeHolder);

                // first row expanded
                holder.rowtype = holder.previewView; // (CheckableLinearLayout)convertView.findViewById(R.id.rowType);
                // holder.username = (TextView)convertView.findViewById(R.id.textUserName);
                holder.postedtime = (TextView) convertView.findViewById(R.id.textPostedTime);


                // zoom for preview.. needs to only be done ONCE, when holder is first created
                holder.preview.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.preview.getTextSize() * _zoom);
                holder.previewLolCounts.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.previewLolCounts.getTextSize() * _zoom);

                // holder.previewUsername.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.previewUsername.getTextSize() * _zoom);
                holder.postedtime.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.postedtime.getTextSize() * _zoom);


                if (_userNameHeight == 0) {
                    _userNameHeight = (int) (holder.previewUsername.getTextSize() * _zoom);
                    setOriginalUsernameHeight(_userNameHeight);
                }
                holder.previewUsername.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.previewUsername.getTextSize() * _zoom);


                convertView.setTag(holder);
            }

            // expanded post
            if (holder == null && (isExpanded)) {
                holder = new ViewHolder();
                holder.containerExp = (LinearLayout) convertView.findViewById(R.id.rowLayoutExp);

                holder.expandedView = (View) convertView.findViewById(R.id.expandedView);
                holder.expandedView2 = (View) convertView.findViewById(R.id.expandedView2);


                // expanded items
                // holder.content = (TextView)convertView.findViewById(R.id.textContent);

                holder.postContent = (LinearLayout) convertView.findViewById(R.id.postContent);

                holder.loading = (ProgressBar) convertView.findViewById(R.id.tview_loadSpinner);

                holder.expLolCounts = (TextView) convertView.findViewById(R.id.textExpPostLolCounts);

                holder.buttonOther = convertView.findViewById(R.id.buttonPostOpt);
                holder.buttonSharePost = convertView.findViewById(R.id.buttonSharePost);
                holder.buttonBlocks = convertView.findViewById(R.id.buttonMoreOptions);
                holder.buttonReply = convertView.findViewById(R.id.buttonReplyPost);
                holder.buttonAllImages = convertView.findViewById(R.id.buttonOpenAllImages);
                holder.buttonLol = convertView.findViewById(R.id.buttonPostLOL);

                holder.buttonNoteEnabled = convertView.findViewById(R.id.buttonNotificationEnabled);
                holder.buttonNoteMuted = convertView.findViewById(R.id.buttonNotificationMuted);

                // zoom for expanded
                // holder.content.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.content.getTextSize() * _zoom);
                holder.expLolCounts.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.expLolCounts.getTextSize() * _zoom);
                /*
                holder.buttonReply.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.buttonReply.getTextSize() * _zoom);
                holder.buttonAllImages.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.buttonAllImages.getTextSize() * _zoom);
                holder.buttonLol.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.buttonLol.getTextSize() * _zoom);
                */

                // buttons are already as small as they can be
                if (_zoom >= 0.9) {
                    ViewGroup.LayoutParams buttonLayout = holder.buttonSharePost.getLayoutParams();
                    buttonLayout.height = (int) Math.floor(buttonLayout.height * _zoom);
                    buttonLayout.width = (int) Math.floor(buttonLayout.width * _zoom);
                    holder.buttonSharePost.setLayoutParams(buttonLayout);

                    buttonLayout = holder.buttonBlocks.getLayoutParams();
                    buttonLayout.height = (int) Math.floor(buttonLayout.height * _zoom);
                    buttonLayout.width = (int) Math.floor(buttonLayout.width * _zoom);
                    holder.buttonBlocks.setLayoutParams(buttonLayout);

                    buttonLayout = holder.buttonOther.getLayoutParams();
                    buttonLayout.height = (int) Math.floor(buttonLayout.height * _zoom);
                    buttonLayout.width = (int) Math.floor(buttonLayout.width * _zoom);
                    holder.buttonOther.setLayoutParams(buttonLayout);

                    buttonLayout = holder.buttonReply.getLayoutParams();
                    buttonLayout.height = (int) Math.floor(buttonLayout.height * _zoom);
                    buttonLayout.width = (int) Math.floor(buttonLayout.width * _zoom);
                    holder.buttonReply.setLayoutParams(buttonLayout);

                    buttonLayout = holder.buttonAllImages.getLayoutParams();
                    buttonLayout.height = (int) Math.floor(buttonLayout.height * _zoom);
                    buttonLayout.width = (int) Math.floor(buttonLayout.width * _zoom);
                    holder.buttonAllImages.setLayoutParams(buttonLayout);

                    buttonLayout = holder.buttonLol.getLayoutParams();
                    buttonLayout.height = (int) Math.floor(buttonLayout.height * _zoom);
                    buttonLayout.width = (int) Math.floor(buttonLayout.width * _zoom);
                    holder.buttonLol.setLayoutParams(buttonLayout);

                    buttonLayout = holder.buttonNoteMuted.getLayoutParams();
                    buttonLayout.height = (int) Math.floor(buttonLayout.height * _zoom);
                    buttonLayout.width = (int) Math.floor(buttonLayout.width * _zoom);
                    holder.buttonNoteMuted.setLayoutParams(buttonLayout);

                    buttonLayout = holder.buttonNoteEnabled.getLayoutParams();
                    buttonLayout.height = (int) Math.floor(buttonLayout.height * _zoom);
                    buttonLayout.width = (int) Math.floor(buttonLayout.width * _zoom);
                    holder.buttonNoteEnabled.setLayoutParams(buttonLayout);
                }
                convertView.setTag(holder);
            }

            // preview titleview
            if (!isExpanded) {
                // this is created by the animator, have to remove it or recycled views get weird
                holder.previewRow.setLayoutTransition(null);

                // reset container modifiers
                holder.rowtype.setModTagsFalse();
                holder.rowtype.setChecked(isExpanded(position));
                if (p.isNWS()) {
                    holder.rowtype.setNWS(true);
                } else if (p.isINF()) {
                    holder.rowtype.setInf(true);
                } else if (p.isPolitical()) {
                    holder.rowtype.setPolitical(true);
                } else if (p.isTangent()) {
                    holder.rowtype.setTangent(true);
                } else if (p.isStupid()) {
                    holder.rowtype.setStupid(true);
                } else {
                    holder.rowtype.refreshDrawableState(); // needed because setmodtagsfalse does not do this
                }

                holder.postedtime.setText(TimeDisplay.getNiceTimeSince(p.getPosted(), _showHoursSince));

                // 5L is used by the postqueue system to indicate the post hasnt been posted yet
                if (p.getPosted() == 5L)
                    holder.postedtime.setText("Posting...");

                // queued posts
                if (p.isPQP() || p.isWorking()) {
                    holder.postingThrobber.setVisibility(View.VISIBLE);
                } else {
                    holder.postingThrobber.setVisibility(View.GONE);
                }

                // support highlight
                holder.preview.setText(applyHighlight(p.getPreview()));
                holder.preview.setLinkTextColor(MainActivity.getThemeColor(getActivity(), R.attr.colorLink));

                holder.previewUsername.setText(applyHighlight(mAnonMode ? "shacker" : p.getUserName()));

                if (p.getUserName().equalsIgnoreCase(_userName)) {
                    // highlight your own posts
                    holder.previewUsername.setTextColor(getResources().getColor(R.color.selfUserName));
                } else if (p.getUserName().equalsIgnoreCase(_OPuserName) && (position != 0)) {
                    holder.previewUsername.setTextColor(getResources().getColor(R.color.OPUserName));
                } else if (p.isFromEmployee()) {
                    holder.previewUsername.setTextColor(getResources().getColor(R.color.emplUserName));
                } else if (p.isFromModerator()) {
                    holder.previewUsername.setTextColor(getResources().getColor(R.color.modUserName));
                } else {
                    holder.previewUsername.setTextColor(MainActivity.getThemeColor(getActivity(), R.attr.colorUsername));
                }

                // donator icon
                holder.previewLimeHolder.setImageResource(android.R.color.transparent);
                holder.previewLimeHolder.setOnClickListener(null);
                holder.previewLimeHolder.setClickable(false);
                if (_displayLimes) {
                    holder.previewLimeHolder.setVisibility(View.VISIBLE);
                    if (_donatorList.contains(":" + p.getUserName().toLowerCase() + ";")) {
                        holder.previewLimeHolder.setImageDrawable(_donatorIcon);
                    } else if (_donatorGoldList.contains(":" + p.getUserName().toLowerCase() + ";")) {
                        holder.previewLimeHolder.setImageDrawable(_donatorGoldIcon);
                    } else if (_donatorQuadList.contains(":" + p.getUserName().toLowerCase() + ";")) {
                        holder.previewLimeHolder.setImageDrawable(_donatorQuadIcon);
                        // easter egg
                        holder.previewLimeHolder.setClickable(true);
                        holder.previewLimeHolder.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AnimationDrawable quad = (AnimationDrawable) mMainActivity.getResources().getDrawable(R.drawable.quaddamage);
                                ((ImageView) v).setImageDrawable(quad);
                                ((ImageView) v).setOnClickListener(null);
                                v.setClickable(false);
                                quad.start();
                            }
                        });
                        /*

                         */
                    } else if (p.getUserName().toLowerCase().equals(AppConstants.USERNAME_TMWTB)) {
                        holder.previewLimeHolder.setImageDrawable(_briefcaseIcon);
                    } else {
                        holder.previewLimeHolder.setVisibility(View.GONE);
                    }
                } else {
                    holder.previewLimeHolder.setVisibility(View.GONE);
                }

                // tree branch
                buildTreeBranches(p, position, holder.treeIcon);


                // highlight newer posts
                if (MainActivity.getThemeId(getActivity()) == R.style.AppThemeWhite) {
                    int color = 0 + (12 * Math.min(p.getOrder(), 10));
                    holder.preview.setTextColor(Color.argb(255, color, color, color));
                } else {
                    int color = 255 - (12 * Math.min(p.getOrder(), 10));
                    holder.preview.setTextColor(Color.argb(255, color, color, color));
                }

                // has the title view been swapped?
                if (isExpanded(position)) {
                    // do things to change preview row
                    holder.preview.setVisibility(View.GONE);
                    holder.previewLolCounts.setVisibility(View.GONE);
                    holder.postedtime.setVisibility(View.VISIBLE);
                    holder.previewUsername.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.previewUserNameSizeBig) * _zoom);

                    final TextView txtusr = holder.previewUsername;
                    holder.previewUsername.setOnClickListener(getUserNameClickListenerForPosition(position, holder.previewUsername));
                    holder.previewUsername.setClickable(true);
                } else {
                    //  preview mode
                    holder.previewUsername.setOnClickListener(null);
                    holder.previewUsername.setClickable(false);
                    holder.preview.setVisibility(View.VISIBLE);
                    holder.previewUsername.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.previewUserNameSize) * _zoom);
                    holder.previewLolCounts.setVisibility(View.VISIBLE);
                    holder.postedtime.setVisibility(View.GONE);
                }

                if ((p.getLolObj() != null) && !isExpanded(position)) {
                    holder.previewLolCounts.setVisibility(View.VISIBLE);
                    holder.previewLolCounts.setText(p.getLolObj().getTagSpan());
                } else {
                    holder.previewLolCounts.setVisibility(View.GONE);
                }


            }


            return convertView;
        }

        class PostClip {
            public static final int TYPE_YOUTUBE = 2;
            public static final int TYPE_IMAGE = 0;
            public static final int TYPE_MP4 = 3;
            public Spannable text = null;
            public CustomURLSpan url = null;
            public int type = 0;

            PostClip(Spannable ptext) {
                text = ptext;
            }

            PostClip(CustomURLSpan pimage, int ptype) {
                url = pimage;
                type = ptype;
            }

            PostClip(Spannable ptext, CustomURLSpan pimage, int ptype) {
                text = ptext;
                url = pimage;
                type = ptype;
            }
        }

        private ArrayList<PostClip> postTextChopper(final Spannable text, boolean removeLinksImages, boolean removeLinksVideos) {
            // this thing chops up posts that have image links into sets up preceeding text and image link following it.
            CustomURLSpan[] list = text.getSpans(0, text.length(), CustomURLSpan.class);
            ArrayList<CustomURLSpan> spanList = new ArrayList<CustomURLSpan>(Arrays.asList(list));

            // bug fix for android n. getSpans no longer gives an ordered list. google is a bag of dicks
            Collections.sort(spanList, new Comparator<CustomURLSpan>() {
                @Override
                public int compare(CustomURLSpan t1, CustomURLSpan t2) {
                    if (text.getSpanStart(t1) > text.getSpanStart(t2))
                        return 1;
                    else if (text.getSpanStart(t1) == text.getSpanStart(t2))
                        return 0;
                    return -1;
                }
            });

            ArrayList<PostClip> returnItem = new ArrayList<PostClip>();
            int startClip = 0;

            for (int i = 0; i < spanList.size(); i++) {
                CustomURLSpan target = spanList.get(i);
                if (PopupBrowserFragment.isImage(target.getURL().trim(), true)) {
                    if ((text.subSequence(startClip, text.getSpanEnd(target)).toString().length() > 0)) {
                        if (removeLinksImages) {
                            Spannable tempTxt = ((Spannable) text.subSequence(startClip, text.getSpanStart(target)));
                            tempTxt.removeSpan(target);
                            returnItem.add(0, new PostClip(tempTxt, target, PostClip.TYPE_IMAGE));
                        } else
                            returnItem.add(0, new PostClip((Spannable) text.subSequence(startClip, text.getSpanEnd(target)), target, PostClip.TYPE_IMAGE));
                    }
                    startClip = text.getSpanEnd(target);
                }
                if ((new YoutubeUriParser(target.getURL().trim())).isYoutube() && _verified) {
                    if ((text.subSequence(startClip, text.getSpanEnd(target)).toString().length() > 0)) {
                        if (removeLinksImages) {
                            Spannable tempTxt = ((Spannable) text.subSequence(startClip, text.getSpanStart(target)));
                            tempTxt.removeSpan(target);
                            returnItem.add(0, new PostClip(tempTxt, target, PostClip.TYPE_YOUTUBE));
                        } else
                            returnItem.add(0, new PostClip((Spannable) text.subSequence(startClip, text.getSpanEnd(target)), target, PostClip.TYPE_YOUTUBE));
                    }
                    startClip = text.getSpanEnd(target);
                }
                if (PopupBrowserFragment.isMP4(target.getURL().trim())) {
                    if ((text.subSequence(startClip, text.getSpanEnd(target)).toString().length() > 0)) {
                        if (removeLinksVideos) {
                            Spannable tempTxt = ((Spannable) text.subSequence(startClip, text.getSpanStart(target)));
                            tempTxt.removeSpan(target);
                            returnItem.add(0, new PostClip(tempTxt, target, PostClip.TYPE_MP4));
                        } else
                            returnItem.add(0, new PostClip((Spannable) text.subSequence(startClip, text.getSpanEnd(target)), target, PostClip.TYPE_MP4));
                    }
                    startClip = text.getSpanEnd(target);
                }
            }
            if (text.length() - startClip > 0) {
                if (text.subSequence(startClip, text.length()).toString().length() > 0)
                    returnItem.add(0, new PostClip((Spannable) text.subSequence(startClip, text.length())));
            }
            return returnItem;
        }

        private CharSequence applyExtLink(Spannable text, TextView t) {
            // this thing puts little world buttons at the end of links to provide link actions
            CustomURLSpan[] list = text.getSpans(0, text.length(), CustomURLSpan.class);
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            for (CustomURLSpan target : list) {
                Drawable iSpan = mMainActivity.getResources().getDrawable(R.drawable.ic_action_action_launch);
                iSpan.setBounds(0, 0, (int) (t.getLineHeight() * 1.35f), (int) (t.getLineHeight() * 1.35f));
                builder.insert(text.getSpanEnd(target), " o");
                builder.setSpan(new ImageSpan(iSpan, DynamicDrawableSpan.ALIGN_BOTTOM), text.getSpanEnd(target) + 1, text.getSpanEnd(target) + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                final String href = target.getURL();
                ClickableSpan clickspan = new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        showLinkOptionsMenu(href);
                    }
                };
                builder.setSpan(clickspan, text.getSpanEnd(target) + 1, text.getSpanEnd(target) + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            return builder;
        }

        private void showLinkOptionsMenu(final String href) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mMainActivity);
            builder.setTitle(href);
            final CharSequence[] items = {"Open Externally", "Open in Popup Browser", "Copy URL", "Share Link", "Change Default Action"};
            builder.setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    if (item == 2) {
                        ClipboardManager clipboard = (ClipboardManager) mMainActivity.getSystemService(Activity.CLIPBOARD_SERVICE);
                        clipboard.setText(href);
                        Toast.makeText(mMainActivity, href, Toast.LENGTH_SHORT).show();
                    }
                    if (item == 3) {
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, href);
                        sendIntent.setType("text/plain");
                        mMainActivity.startActivity(Intent.createChooser(sendIntent, "Share Link"));
                    }
                    if (item == 0) {
                        Uri u = Uri.parse(href);
                        if (u.getScheme() == null) {
                            u = Uri.parse("http://" + href);
                        }
                        Intent i = new Intent(Intent.ACTION_VIEW,
                                u);
                        mMainActivity.startActivity(i);
                    }
                    if (item == 1) {
                        mMainActivity.openBrowser(href);
                    }
                    if (item == 4) {
                        MaterialDialog.Builder build = new MaterialDialog.Builder(mMainActivity);
                        String[] descs = mMainActivity.getResources().getStringArray(R.array.preference_popupBrowser);
                        final String[] vals = mMainActivity.getResources().getStringArray(R.array.preference_popupBrowser_vals);
                        build.items(descs);
                        build.title(mMainActivity.getResources().getString(R.string.preference_popup_browser_title));
                        build.itemsCallbackSingleChoice(Integer.parseInt(_prefs.getString("usePopupBrowser2", "1")), new MaterialDialog.ListCallbackSingleChoice() {
                            @Override
                            public boolean onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
                                SharedPreferences.Editor edit = _prefs.edit();
                                edit.putString("usePopupBrowser2", vals[i]);
                                edit.apply();
                                return true;
                            }
                        });
                        build.show();

                    }
                }
            });
            AlertDialog alert = builder.create();
            alert.setCanceledOnTouchOutside(true);
            alert.show();
        }

        private CharSequence applyHighlight(String preview) {
            if ((_highlight != null) && (_highlight.length() > 0)) {
                return applyHighlight(new SpannableString(preview));
            } else {
                return preview;
            }
        }

        private CharSequence applyHighlight(Spannable preview) {
            if ((_highlight != null) && (_highlight.length() > 0)) {
                Spannable text = preview;
                String txtplain = text.toString().toLowerCase();
                int color = getResources().getColor(R.color.modtag_political);
                Spannable highlighted = new SpannableString(text);
                int startSpan = 0, endSpan = 0;
                String target = _highlight;
                while (true) {
                    startSpan = txtplain.indexOf(target, endSpan);
                    BackgroundColorSpan foreColour = new BackgroundColorSpan(color);
                    // Need a NEW span object every loop, else it just moves the span
                    if (startSpan < 0) {
                        break;
                    }
                    endSpan = startSpan + target.length();
                    highlighted.setSpan(foreColour, startSpan, endSpan, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                return highlighted;
            } else {
                return preview;
            }
        }

        private ImageView buildTreeBranches(Post t, int position, ImageView imageView) {
            StringBuilder depthStr = new StringBuilder(t.getDepthStringFormatted());

            //if (depthStr.length() > 0)
            //{
            final String imageKey = t.getDepthStringFormatted();

            final Bitmap bitmap = getBitmapFromMemCache("x" + imageKey);
            if (bitmap == null) {
                System.out.println("HAD TO REBUILD CACHE");
                imageView.setImageBitmap(buildBranchesForPost(t));
            } else {
                imageView.setImageBitmap(bitmap);
            }


            imageView.forceLayout();
            imageView.setAdjustViewBounds(true);
            return imageView;
        }


        private Bitmap buildBranchesForPost(Post t) {
            int bulletWidth = _bulletBlank.getWidth();
            StringBuilder depthStr = new StringBuilder(t.getDepthStringFormatted());

            Bitmap big;
            if (depthStr.length() > 0)
                big = Bitmap.createBitmap(bulletWidth * depthStr.length(), _bulletBlank.getHeight(), Bitmap.Config.ARGB_4444);
            else
                big = Bitmap.createBitmap(_bulletSpacer.getWidth(), _bulletBlank.getHeight(), Bitmap.Config.ARGB_4444);

            Canvas canvas = new Canvas(big);

            if (depthStr.length() > 0) {
                for (int i = 0; i < depthStr.length(); i++) {

                    if (depthStr.charAt(i) == "L".charAt(0))
                        canvas.drawBitmap(_bulletEnd, bulletWidth * i, 0, null);
                    if (depthStr.charAt(i) == "T".charAt(0))
                        canvas.drawBitmap(_bulletBranch, bulletWidth * i, 0, null);
                    if (depthStr.charAt(i) == "|".charAt(0))
                        canvas.drawBitmap(_bulletExtendPast, bulletWidth * i, 0, null);
                    if (depthStr.charAt(i) == "[".charAt(0))
                        canvas.drawBitmap(_bulletEndNew, bulletWidth * i, 0, null);
                    if (depthStr.charAt(i) == "+".charAt(0))
                        canvas.drawBitmap(_bulletBranchNew, bulletWidth * i, 0, null);
                    if (depthStr.charAt(i) == "!".charAt(0))
                        canvas.drawBitmap(_bulletExtendPastNew, bulletWidth * i, 0, null);
                    if (depthStr.charAt(i) == "0".charAt(0))
                        canvas.drawBitmap(_bulletBlank, bulletWidth * i, 0, null);
                    if (depthStr.charAt(i) == "C".charAt(0))
                        canvas.drawBitmap(_bulletCollapse, bulletWidth * i, 0, null);


                }
                addBitmapToMemoryCache("x" + t.getDepthStringFormatted(), big);
                return big;
            } else {
                canvas.drawBitmap(_bulletSpacer, 0, 0, null);
                addBitmapToMemoryCache("x", big);
            }
            return null;
        }

        private Bitmap createBullet(int id) {
            Bitmap bm = BitmapFactory.decodeResource(getResources(), id);
            if (_zoom != 1.0f) {
                int newH = (int) (bm.getHeight() * _zoom);
                int newW = (int) (bm.getWidth() * _zoom);
                bm = Bitmap.createScaledBitmap(bm, newW, newH, false);
            }
            return bm;
        }

        private void createAllBullets() {
            _bulletSpacer = createBullet(R.drawable.bullet_spacer);
            _bulletBlank = createBullet(R.drawable.bullet_blank);
            _bulletEnd = createBullet(R.drawable.bullet_end);
            _bulletExtendPast = createBullet(R.drawable.bullet_extendpast);
            _bulletBranch = createBullet(R.drawable.bullet_branch);
            _bulletEndNew = createBullet(R.drawable.bullet_endnew);
            _bulletExtendPastNew = createBullet(R.drawable.bullet_extendpastnew);
            _bulletBranchNew = createBullet(R.drawable.bullet_branchnew);
            _bulletCollapse = createBullet(R.drawable.bullet_collapse);
            _bulletWidth = _bulletCollapse.getWidth();

            // donator icon
            int size = (int) (_bulletCollapse.getHeight() * 0.75);
            BitmapDrawable bm = new BitmapDrawable(getContext().getResources(), Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher), size, size, false));
        	/*ColorMatrix cm = new ColorMatrix();
        	cm.setSaturation(0.8f);
        	final float m[] = cm.getArray();
        	final float c = 0.8f;
        	final float bright = 0.6f;
        	cm.set(new float[] {
        	        m[ 0] * c, m[ 1] * c, m[ 2] * c, m[ 3] * c, m[ 4] * c + bright,
        	        m[ 5] * c, m[ 6] * c, m[ 7] * c, m[ 8] * c, m[ 9] * c + bright,
        	        m[10] * c, m[11] * c, m[12] * c, m[13] * c, m[14] * c + bright,
        	        m[15]    , m[16]    , m[17]    , m[18]    , m[19] });
        	        */

            int dimmer = 175;
            if (MainActivity.getThemeId(getActivity()) == R.style.AppThemeWhite) {
                dimmer = 230;
            }
            bm.setColorFilter(new LightingColorFilter(Color.argb(1, dimmer, dimmer, dimmer), 0));
            // bm.setColorFilter(new ColorMatrixColorFilter(cm));
            _donatorIcon = bm;

            bm = new BitmapDrawable(getContext().getResources(), Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.limegold2018), size, size, false));
            bm.setColorFilter(new LightingColorFilter(Color.argb(1, dimmer, dimmer, dimmer), 0));
            _donatorGoldIcon = bm;

            bm = new BitmapDrawable(getContext().getResources(), Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.iconpowerup_quad), size, size, false));
            bm.setColorFilter(new LightingColorFilter(Color.argb(1, dimmer, dimmer, dimmer), 0));
            _donatorQuadIcon = bm;

            bm = new BitmapDrawable(getContext().getResources(), Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.briefcaseicon), size, size, false));
            bm.setColorFilter(new LightingColorFilter(Color.argb(1, dimmer, dimmer, dimmer), 0));
            _briefcaseIcon = bm;
        }

        @Override
        protected ArrayList<Post> loadData() throws Exception {
            if ((_rootPostId > 0) && (_messageId == 0)) {
                ArrayList<Post> posts = ShackApi.processPosts(ShackApi.getPosts(_rootPostId, this.getContext(), ShackApi.getBaseUrl(mMainActivity)), _rootPostId, _maxBullets, mMainActivity);
                _shackloldata = ShackApi.getLols(mMainActivity);

                if (_shackloldata.size() != 0) {
                    // check if this thread has shacklol data
                    if (_shackloldata.containsKey(Integer.toString(_rootPostId))) {
                        _threadloldata = _shackloldata.get(Integer.toString(_rootPostId));
                    }
                }

                if (posts.size() > 0) {
                    // set op username for hilights
                    _OPuserName = posts.get(0).getUserName();
                }
	/*
	            if ((this.getCount() > 0) && (posts.size() > 0))
	            {
	            	// make sure the first items of both arent dupes from preloading the root post
	            	if (this.getItem(0).getPostId() == posts.get(0).getPostId())
	            	{
	            		posts.remove(0);
	            	}
	            }
*/
                for (Post p : posts) {
                    //

                    // load lols
                    if ((_getLols) && (_lolsInPost) && (_threadloldata != null) && (_threadloldata.containsKey(Integer.toString(p.getPostId())))) {
                        p.setLolObj(_threadloldata.get(Integer.toString(p.getPostId())));
                    }

                    // derelict filter
                    if (((MainActivity) getActivity()).isOnBlocklist(p.getUserName()) && (mEchoEnabled) && (mEchoPalatize)) {
                        p.recreatePost(p.getPostId(), p.getUserName(), getVastlyImprovedDerelictPost(getActivity()), p.getPosted(), p.getLevel(), p.getModeration(), p.getExpanded(), p.getSeen(), p.isPQP());
                    }
                }

                // fast scroll on mega threads
                if (mMainActivity != null) {
                    boolean set = false;
                    if ((posts.size() > 400))
                        set = true;
                    final boolean set2 = set && _fastScroll;

                    mMainActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (_viewAvailable) {
                                if (getListView() != null) {
                                    getListView().setFastScrollEnabled(set2);
                                }
                            }
                        }
                    });
                }
                // reduce the number of post images which must be generated on the fly


                Long timer = TimeDisplay.now();
                fakePostAddinator(posts);
                System.out.println("TIMER: FPA: " + (TimeDisplay.now() - timer));
                timer = TimeDisplay.now();
                createThreadTree(posts);
                System.out.println("TIMER: CTT: " + (TimeDisplay.now() - timer));
                timer = TimeDisplay.now();
                prePreBuildTreeCache(posts);

                statMax(mMainActivity, "MaxThreadSize", posts.size());

                return posts;
            } else
                return null;
        }

        @Override
        public void beforeClear() {
            setListAdapter(null);
        }

        @Override
        public void afterDisplay() {

            Long timer = TimeDisplay.now();

            expandAndCheckPostWithoutAnimation(0);

            // pull to refresh integration
            ptrLayout.setRefreshing(false);

            System.out.println("TIMER: SRC: " + (TimeDisplay.now() - timer));
            timer = TimeDisplay.now();


            // attempt at creating json so thread can be saved saveThread()
            // this is for when we receive a loading command via intent without a thread json at the same time
            boolean hasReplied = false;
            String userName = _prefs.getString("userName", "");

            if (userName.length() > 0) {
                for (int i = 0; i < _adapter.getCount(); i++) {
                    if (_adapter.getItem(i).getUserName().equals(userName))
                        hasReplied = true;
                }
            }
            System.out.println("TIMER: hasReplied: " + (TimeDisplay.now() - timer));
            timer = TimeDisplay.now();

            Thread t = null;
            if ((_adapter != null) && (_adapter.getCount() > 0) && (_messageId == 0)) {
                // create fake thread for fav saving
                t = new Thread(_adapter.getItem(0).getPostId(), _adapter.getItem(0).getUserName(), _adapter.getItem(0).getContent(), _adapter.getItem(0).getPosted(), _adapter.getCount(), "ontopic", hasReplied, mMainActivity.mOffline.containsThreadId(_rootPostId));
                if (_adapter.getItem(0) != null) {
                    _rootPostId = _adapter.getItem(0).getPostId();
                }
            }

            if (t != null) {
                // create data for fav saving
                if (t.getJson() != null)
                    _lastThreadJson = t.getJson();
            }
            System.out.println("TIMER: fakeJson: " + (TimeDisplay.now() - timer));
            timer = TimeDisplay.now();
            // autofave. TODO: MAKE WORK WITH POSTQUEUE
            if (_autoFaveOnLoad) {
                saveThread();
                _autoFaveOnLoad = false;
            }

            // mark as read if in favs
            if (mMainActivity.mOffline.containsThreadId(_rootPostId)) {
                mMainActivity.markFavoriteAsRead(_rootPostId, _adapter.getCount());
            }
            System.out.println("TIMER: markRead: " + (TimeDisplay.now() - timer));
            timer = TimeDisplay.now();


            // select posts for _selectpostidafterloading
            setListAdapter(this);
            ensurePostSelectedAndDisplayed();


            System.out.println("TIMER: EPS: " + (TimeDisplay.now() - timer));
            timer = TimeDisplay.now();
            updateThreadViewUi();
            System.out.println("TIMER: updUI: " + (TimeDisplay.now() - timer));
            timer = TimeDisplay.now();
        }

        private class ViewHolder {
            public LinearLayout previewUsernameHolder;
            public LinearLayout containerExp;
            public ProgressBar postingThrobber;
            public ImageButton buttonLol;
            public ImageView previewLimeHolder;
            public View anchor;
            public int currentPostId;
            public ImageButton buttonAllImages;
            public View expandedView2;
            public TableRow previewRow;
            public ProgressBar loading;
            public CheckableTableLayout rowtype;

            ImageView treeIcon;

            /* OLD WAY TO DO BRANCHES
			LinearLayout treeIcon;
			public LinearLayout treeIconExp;
			*/
            public TextView previewUsername;
            public ImageButton buttonRefresh;
            // public ImageButton buttonOther;
            public ImageButton buttonReply;
            public TextView expLolCounts;
            public TextView previewLolCounts;
            public View expandedView;
            public CheckableTableLayout previewView;
            public LinearLayout postContent;

            TextView content;
            TextView preview;
            //TextView username;
            TextView postedtime;
            public ImageButton buttonNoteEnabled;
            public ImageButton buttonNoteMuted;
            public ImageButton buttonSharePost;

            public ImageButton buttonBlocks;
            public ImageButton buttonOther;
        }

        public ArrayList<Post> fakePostAddinator(ArrayList<Post> posts) {
            String selfUserName = _prefs.getString("userName", "default user");
            // get postqueueposts
            PostQueueDB pqpdb = new PostQueueDB(mMainActivity);
            pqpdb.open();
            ArrayList<PostQueueObj> pqplist = (ArrayList<PostQueueObj>) pqpdb.getAllPostsInQueue(false);
            pqpdb.close();

            ArrayList<Integer> parentIds = new ArrayList<Integer>();

            for (PostQueueObj pqo : pqplist) {
                parentIds.add(pqo.getReplyToId());
            }

            ArrayList<Integer> postIds = new ArrayList<Integer>();
            for (int i = 0; i < posts.size(); i++) {
                if (!posts.get(i).isPQP()) {
                    postIds.add(posts.get(i).getPostId());
                }
            }

            for (int i = 0; i < posts.size(); i++) {
                Post curPost = posts.get(i);

                // add a fake reply if we have a matching post in the adapter that has a replytoid of this postid
                if (parentIds.contains(curPost.getPostId())) {
                    for (PostQueueObj pqo : pqplist) {
                        // System.out.println("ADDINATOR: replytoid : curpostid " + pqo.getReplyToId() + ":"+ curPost.getPostId());
                        // a parent is found and the real post with the same finalid as this postqueuedpost doesnt yet exist
                        if ((pqo.getReplyToId() == curPost.getPostId()) && (!postIds.contains(pqo.getFinalId()))) {
                            String body = ComposePostView.getPreviewFromHTML(pqo.getBody());
                            Post fakePost = new Post((pqo.getFinalId() == 0) ? Integer.parseInt(Long.toString(pqo.getPostQueueId())) : pqo.getFinalId(), selfUserName, body, (pqo.getFinalId() == 0) ? 5L : TimeDisplay.now(), curPost.getLevel() + 1, "ontopic", false, false, (pqo.getFinalId() == 0) ? true : false);
                            // copy the depth string from parent
                            //fakePost.setDepthString(curPost.getDepthString().substring(0, curPost.getDepthString().length() - 1) + " " + "L");

                            //if (getBitmapFromMemCache(fakePost.getDepthString()) == null)
                            //	addBitmapToMemoryCache(fakePost.getDepthString(), buildBranchesForPost(fakePost));

                            fakePost.setOrder(0);

                            // find right place
                            int place = 1;
                            for (int j = 1; true; j++) {
                                if ((posts.size() > i + j) && (posts.get(i + j).getLevel() < curPost.getLevel() + 1)) {
                                    place = i + j;
                                    break;
                                }
                                if (posts.size() <= i + j) {
                                    place = i + j;
                                    break;
                                }
                            }
                            // System.out.println("ADDINATOR: adding pqo" + pqo.getPostQueueId()+ " to place" + place + " " + fakePost.getContent() + " " + fakePost.getPostId());
                            posts.add(place, fakePost);
                        }
                    }
                }
            }
            return posts;
        }

        public void fakePostRemoveinator() {
            System.out.println("THREADVIEW POSTQU: Got signal to remove fakeposts");
            if (_adapter != null) {
                for (int i = 0; i < _adapter.getCount(); i++) {
                    Post post = _adapter.getItem(i);
                    if (post.isPQP()) {
                        _adapter.remove(post);
                        i--;
                    }
                }
            }
        }

        public ArrayList<Post> createThreadTree(ArrayList<Post> posts) {
            // echo chamber
            if ((mEchoEnabled) && (!mEchoPalatize)) {
                ListIterator<Post> iter = posts.listIterator();
                int level = 9001; // a high level, like over 9000
                while (iter.hasNext()) {
                    Post p = iter.next();
                    if (p.getLevel() > level) {
                        iter.remove();
                        statInc(getActivity(), "EchoChamberRemovedReply");
                        continue;
                    } else {
                        // reset
                        level = 9001;
                    }
                    // echo chamber
                    if (((MainActivity) getActivity()).isOnBlocklist(p.getUserName())) {
                        level = p.getLevel();
                        iter.remove();
                        statInc(getActivity(), "EchoChamberRemoved");
                    }
                }
            }

            // end echo chamber

            // create depthstrings
            for (int i = 0; i < posts.size(); i++) {
                int j = i - 1;

                // iterate backwards from current post
                // while (havent hit top of thread) AND (level of indentation is >= to current post)
                while ((j > 0) && (posts.get(j).getLevel() >= posts.get(i).getLevel())) {
                    StringBuilder jDString = new StringBuilder(posts.get(j).getDepthString());

                    // L is a seen reply |_, [ is unseen green line |_
                    if ((jDString.charAt(posts.get(i).getLevel() - 1) == "L".charAt(0)) && (posts.get(i).getLevel() == posts.get(j).getLevel())) {
                        jDString.setCharAt(posts.get(i).getLevel() - 1, "T".charAt(0));
                    }
                    if ((jDString.charAt(posts.get(i).getLevel() - 1) == "[".charAt(0)) && (posts.get(i).getLevel() == posts.get(j).getLevel())) {
                        jDString.setCharAt(posts.get(i).getLevel() - 1, "+".charAt(0));
                    }
                    // 0 denotes blank space
                    if (jDString.charAt(posts.get(i).getLevel() - 1) == "0".charAt(0)) {
                        // ! denotes green line, | denotes gray
                        if (posts.get(i).getSeen())
                            jDString.setCharAt(posts.get(i).getLevel() - 1, "|".charAt(0));
                        else
                            jDString.setCharAt(posts.get(i).getLevel() - 1, "!".charAt(0));
                    }

                    posts.get(j).setDepthString(jDString.toString());
                    j--;
                }
            }

            // collapser for deep threads
            for (int i = 0; i < posts.size(); i++) {
                StringBuilder depthStr = new StringBuilder(posts.get(i).getDepthString());

                // collapser for deep threads
                if (depthStr.length() >= _maxBullets) {
                    int j = 0;
                    String depthStr2 = depthStr.toString();
                    while (depthStr2.length() > _maxBullets) {
                        // the higher this number the higher the chunking of the collapser. higher numbers are arguably better to a point
                        depthStr2 = depthStr2.substring(Math.round((float) (_maxBullets * 0.75f)));
                        j++;
                    }
                    if (j > 0) {
                        String repeated = new String(new char[j]).replace("\0", "C");
                        // String repeated2 = new String(new char[(depthStr2.length() - 1)]).replace("\0", "0");
                        String repeated2 = depthStr2.substring(j);
                        depthStr = new StringBuilder(repeated + repeated2);
                    }
                }

                posts.get(i).setDepthStringFormatted(depthStr.toString());
            }
            return posts;
        }

        // used on posts
        public void prePreBuildTreeCache(ArrayList<Post> posts) {
            // prebuild tree branches (optimization) during loading instead of when scrolling
            for (int i = 0; i < posts.size(); i++) {
                Post p = posts.get(i);
                if (getBitmapFromMemCache("x" + p.getDepthStringFormatted()) == null)
                    addBitmapToMemoryCache("x" + p.getDepthStringFormatted(), buildBranchesForPost(p));
            }
        }

    }

    public void adjustSelected(int movement) {
        if (_viewAvailable) {
            if (_lastExpanded != _adapter.getExpandedPosition() && _adapter.getExpandedPosition() != -1)
                _lastExpanded = _adapter.getExpandedPosition();
            final int index = _lastExpanded + movement;
            if (index >= 0 && index < getListView().getCount()) {
                ensureVisible(index, true, true, false);
            }
        }
    }

    void ensureVisible(int position, boolean withPostMove, final boolean withExpansion, final boolean forcePostToTop) {
        ListView view = getListView();

        if (view != null) {

            if (position < 0 || position >= view.getCount()) {
                return;
            }

            int firstPositionVisible = view.getFirstVisiblePosition();
            int lastPositionVisible = view.getLastVisiblePosition();
            int destination = 0;

            if (position < firstPositionVisible)
                destination = position;
            else if (position >= lastPositionVisible)
                destination = (position - (lastPositionVisible - firstPositionVisible));

            if ((position < firstPositionVisible) || forcePostToTop) {
                view.setSelectionFromTop(destination, 0);
            } else if (position >= lastPositionVisible) {
                System.out.println("STUFF:L " + (lastPositionVisible - firstPositionVisible) + " gvt:" + view.getChildCount());
                view.setSelectionFromTop(lastPositionVisible + 1, view.getChildAt(lastPositionVisible - firstPositionVisible).getBottom() - 5);
            }

            if (withPostMove) {
                // keep the child view on screen
                final int pos = position;
                final ListView listView = view;
                listView.post(new Runnable() {

                    @Override
                    public void run() {
                        View betterView = getListViewChildAtPosition(pos, listView);
                        if (betterView != null) {
                            listView.requestChildRectangleOnScreen(betterView, new Rect(0, 0, betterView.getRight(), betterView.getHeight()), false);
                        } else
                            listView.smoothScrollToPosition(pos);

                        if (withExpansion) {
                            listView.post(new Runnable() {

                                @Override
                                public void run() {
                                    _adapter.expand(pos);
                                }
                            });
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onPause() {
        saveListState();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        //  update all PQPs
        if ((_adapter != null) && (mMainActivity != null)) {
            for (int i = 0; i < _adapter.getCount(); i++) {
                if (_adapter.getItem(i).isPQP()) {
                    System.out.println("getting db backed pqo from id " + _adapter.getItem(i).getPostId());
                    PostQueueObj pqo = PostQueueObj.FromDB(_adapter.getItem(i).getPostId(), mMainActivity);
                    if ((pqo != null) && (pqo.getFinalId() != 0)) {
                        _adapter.getItem(i).setPostId(pqo.getFinalId());
                        _adapter.getItem(i).setIsPQP(false);
                        _adapter.getItem(i).setPosted(TimeDisplay.now());
                    }
                }
            }
            _adapter.notifyDataSetChanged();

            saveListState();
            setListAdapter(_adapter);
            _adapter.setAbsListView(getListView());
            restoreListState();
        }
    }

    public void updatePQPostIdToFinal(int PQPId, int finalId) {
        System.out.println("THREADVIEW POSTQU: Got signal to update PQPid");
        if (_adapter != null) {
            boolean found = false;
            int length = _adapter.getCount();
            for (int i = 0; i < length; i++) {
                Post post = _adapter.getItem(i);
                if ((post.isPQP()) && (post.getPostId() == PQPId)) {
                    post.setPostId(finalId);
                    post.setIsPQP(false);
                    post.setPosted(TimeDisplay.now());
                    found = true;
                    // this updates blue dot and increases reply count in thread list
                    mMainActivity.attemptToUpdateReplyCountInThreadListTo(_rootPostId, length, true);
                    break;
                }
            }
            // update this too
            if (_selectPostIdAfterLoading == PQPId) {
                System.out.println("SPIALID UPDATED FROM PQPID TO REALID");
                _selectPostIdAfterLoading = finalId;
                _isSelectPostIdAfterLoadingIdaPQPId = false;
            }

            if (found)
                _adapter.notifyDataSetChanged();
        }

    }

    public void removePQPostId(int PQPId) {
        System.out.println("removePQPostId: Got signal to update PQPid" + PQPId);
        if (_adapter == null) {
            return;
        }
        int length = _adapter.getCount();
        for (int i = 0; i < length; i++) {
            Post post = _adapter.getItem(i);
            if ((post.isPQP()) && (post.getPostId() == PQPId)) {
                _adapter.remove(i);
                _adapter.notifyDataSetChanged();
                return;
            }
        }
    }

    class StyleCallback implements ActionMode.Callback {

        private TextView mTextView;

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.text_selection, menu);

            return true;
        }

        public void setTextView(TextView content) {
            mTextView = content;
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            menu.findItem(R.id.menu_textSelectSearch).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.findItem(R.id.menu_textSelectSearchGoogle).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            if (menu.findItem(android.R.id.selectAll) != null) {
                menu.findItem(android.R.id.selectAll).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }
            return false;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            CharacterStyle cs;
            TextView bodyView = mTextView;
            int start = bodyView.getSelectionStart();
            int end = bodyView.getSelectionEnd();
            SpannableStringBuilder ssb = new SpannableStringBuilder(bodyView.getText());

            Bundle args;
            switch (item.getItemId()) {

                case R.id.menu_textSelectSearch:
                    args = new Bundle();
                    args.putString("terms", bodyView.getText().subSequence(start, end).toString());
                    mMainActivity.openSearch(args);
                    return true;

                case R.id.menu_textSelectSearchGoogle:
                    try {
                        String escapedQuery = URLEncoder.encode(bodyView.getText().subSequence(start, end).toString(), "UTF-8");
                        Uri uri = Uri.parse("https://www.google.com/search?q=" + escapedQuery);
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return true;
            }
            return false;
        }

        public void onDestroyActionMode(ActionMode mode) {
        }
    }

    public static void expandView(final View v) {
        if (v.getVisibility() == View.VISIBLE)
            return;

        v.measure(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        final int targetHeight = v.getMeasuredHeight();

        // Older versions of android (pre API 21) cancel animations for views with a height of 0.
        v.getLayoutParams().height = 1;
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                v.getLayoutParams().height = interpolatedTime == 1
                        ? LinearLayout.LayoutParams.WRAP_CONTENT
                        : (int) (targetHeight * interpolatedTime);
                v.requestLayout();
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        // 1dp/ms
        a.setDuration((int) (targetHeight / v.getContext().getResources().getDisplayMetrics().density) * 6);
        a.setInterpolator(new DecelerateInterpolator(1.0f));
        v.startAnimation(a);
    }

    public static void collapseView(final View v, boolean instantly) {
        if (v.getVisibility() == View.GONE)
            return;

        if (instantly) {
            v.setVisibility(View.GONE);
            return;
        }

        final int initialHeight = v.getMeasuredHeight();

        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    v.setVisibility(View.GONE);
                } else {
                    v.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        // 1dp/ms
        a.setDuration((int) (initialHeight / v.getContext().getResources().getDisplayMetrics().density) * 6);
        a.setInterpolator(new DecelerateInterpolator(1.0f));
        v.startAnimation(a);
    }


    public static String getVastlyImprovedDerelictPost(Context ctx) {
        String[] array = ctx.getResources().getStringArray(R.array.derelictposts);
        return array[randInt(0, (array.length - 1))];
    }

}
