/**
 * Copyright (c) 2013-2014. Francisco Contreras, Holland Salazar.
 * Copyright (c) 2015. Tobias Strebitzer, Francisco Contreras, Holland Salazar.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 * Neither the name of the Baker Framework nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/
package com.bakerframework.baker.activity;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import com.bakerframework.baker.BakerApplication;
import com.bakerframework.baker.R;
import com.bakerframework.baker.model.BookJson;
import com.bakerframework.baker.model.Issue;
import com.bakerframework.baker.settings.Configuration;
import com.bakerframework.baker.view.CustomWebView;
import com.bakerframework.baker.view.CustomWebViewPager;
import com.bakerframework.baker.view.WebViewFragment;
import com.bakerframework.baker.view.WebViewFragmentPagerAdapter;
import com.viewpagerindicator.LinePageIndicator;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class IssueActivity extends FragmentActivity {

    private boolean doubleTap = false;
    private boolean enableDoubleTap = true;
    private boolean enableBackNextButton = false;

	private GestureDetectorCompat gestureDetector;
	private WebViewFragmentPagerAdapter webViewPagerAdapter;
	private CustomWebViewPager pager;
    private BookJson jsonBook;

    public final static String MODAL_URL = "com.giniem.gindpubs.MODAL_URL";
    public final static String ORIENTATION = "com.giniem.gindpubs.ORIENTATION";

    private String orientation;

    private boolean STANDALONE_MODE = false;
    private boolean RETURN_TO_SHELF = false;
    private boolean ENABLE_BACK_NEXT_BUTTONS = false;
    private boolean ENABLE_DOUBLE_TAP = true;
    private boolean ENABLE_TUTORIAL = false;

    public BookJson getJsonBook() {
        return this.jsonBook;
    }

    public CustomWebViewPager getPager() {
        return this.pager;
    }

    private Issue issue;

    @Override
    protected void onDestroy() {
        Log.d(this.getClass().getName(), "Called onDestroy method.");
        WebViewFragment fragment = (WebViewFragment) webViewPagerAdapter.instantiateItem(pager, pager.getCurrentItem());
        fragment.getWebView().destroy();
        super.onDestroy();
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// We would like to keep the screen on while reading the magazine
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Remove title bar
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);

		// Remove notification bar
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.html_pager);

		Intent intent = getIntent();

        // Get issue
        String issueName = intent.getStringExtra(Configuration.ISSUE_NAME);
        issue = BakerApplication.getInstance().getIssueCollection().getIssueByName(issueName);

		try {
            STANDALONE_MODE = intent.getBooleanExtra(Configuration.ISSUE_STANDALONE, false);
            RETURN_TO_SHELF = intent.getBooleanExtra(Configuration.ISSUE_RETURN_TO_SHELF, true);
            ENABLE_DOUBLE_TAP = intent.getBooleanExtra(Configuration.ISSUE_ENABLE_DOUBLE_TAP, true);
            ENABLE_BACK_NEXT_BUTTONS = intent.getBooleanExtra(Configuration.ISSUE_ENABLE_BACK_NEXT_BUTTONS, false);
            ENABLE_TUTORIAL = intent.getBooleanExtra(Configuration.ISSUE_ENABLE_TUTORIAL, false);

            Log.d(this.getClass().getName(), "Will run in standalone mode: " + STANDALONE_MODE);
            if (!RETURN_TO_SHELF) {
                setResult(ShelfActivity.STANDALONE_MAGAZINE_ACTIVITY_FINISH);
            } else {
                setResult(0);
            }

			jsonBook = new BookJson();
            jsonBook.setIssueName(issueName);
            Log.d(this.getClass().toString(), "THE RAW BOOK.JSON IS: " + intent.getStringExtra(Configuration.BOOK_JSON_KEY));
            jsonBook.fromJsonString(intent.getStringExtra(Configuration.BOOK_JSON_KEY));

            this.setOrientation(jsonBook.getOrientation());
            this.setPagerView(jsonBook);

            this.setEnableDoubleTap(ENABLE_DOUBLE_TAP);
            this.setEnableBackNextButton(ENABLE_BACK_NEXT_BUTTONS);

            detectFirstOrLastPage();

			gestureDetector = new GestureDetectorCompat(this,
					new MyGestureListener());
		} catch (Exception ex) {
			ex.printStackTrace();
			Toast.makeText(this, "Not valid book.json found!",
					Toast.LENGTH_LONG).show();
		}

        // Plugin Callback
        BakerApplication.getInstance().getPluginManager().onIssueActivityCreated(this);
	}

    private void detectFirstOrLastPage() {

        if (!isEnableBackNextButton()) {
            return;
        }

        int allItems = this.getJsonBook().getContents().size();
        int currentItem = this.pager.getCurrentItem();

        if (currentItem == (allItems - 1)) {
            Log.d(this.getClass().getName(), "Last page detected.");
            ((Button)findViewById(R.id.buttonNext)).setText(getString(R.string.lbl_finish));

            if (allItems > 1) {
                findViewById(R.id.buttonBack).setVisibility(View.VISIBLE);
            }
        } else if (currentItem == 0) {
            Log.d(this.getClass().getName(), "First page detected.");
            findViewById(R.id.buttonBack).setVisibility(View.GONE);
            ((Button)findViewById(R.id.buttonNext)).setText(getString(R.string.lbl_next));
        } else {
            findViewById(R.id.buttonBack).setVisibility(View.VISIBLE);
            ((Button)findViewById(R.id.buttonNext)).setText(getString(R.string.lbl_next));
        }
    }

    private void goNext() {
        int currentItem = this.pager.getCurrentItem();
        int nextItem = currentItem + 1;
        int allItems = this.getJsonBook().getContents().size();

        Log.d(this.getClass().getName(), "All items: " + allItems + ", current item: " + currentItem + ", next item: " + nextItem);

        if (nextItem < allItems) {
            this.pager.setCurrentItem(nextItem);
            this.detectFirstOrLastPage();
        } else if (nextItem == allItems) {
            this.finish();
        }
    }

    private void goBack() {
        int currentItem = this.pager.getCurrentItem();
        int nextItem = currentItem - 1;
        int allItems = this.getJsonBook().getContents().size();

        Log.d(this.getClass().getName(), "All items: " + allItems + ", current item: " + currentItem + ", next item: " + nextItem);

        if (nextItem >= 0) {
            this.pager.setCurrentItem(nextItem);
            this.detectFirstOrLastPage();
        }
    }

    private void setOrientation(String _orientation) {

        _orientation = _orientation.toUpperCase();
        this.orientation = _orientation;

        final String PORTRAIT = "PORTRAIT";
        final String LANDSCAPE = "LANDSCAPE";
        switch (_orientation) {
            case PORTRAIT:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case LANDSCAPE:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            default:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                break;
        }
    }

    public void resetOrientation() {
        this.setOrientation(this.orientation);
    }

    public boolean isEnableDoubleTap() {
        return enableDoubleTap;
    }

    public void setEnableDoubleTap(boolean enableDoubleTap) {
        this.enableDoubleTap = enableDoubleTap;
    }

    public boolean isEnableBackNextButton() {
        return enableBackNextButton;
    }

    public void setEnableBackNextButton(boolean enableBackNextButton) {
        this.enableBackNextButton = enableBackNextButton;

        if (enableBackNextButton) {
            findViewById(R.id.buttonNext).setVisibility(View.VISIBLE);
            // Click on the next button
            findViewById(R.id.buttonNext).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    goNext();
                }
            });

            // No need for a "Back" button when there's only one page.
            if (this.getJsonBook().getContents().size() > 1) {
                findViewById(R.id.buttonBack).setVisibility(View.VISIBLE);
                // Click on the back button
                findViewById(R.id.buttonBack).setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        goBack();
                    }
                });
            }

        }
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.magazine, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

    private Map<String, String> getBakerMetaTags(final String htmlPath) {
        Map<String, String> values = new HashMap<>();

        Log.d(this.getClass().getName(), "Trying to parse: " + htmlPath);
        try {
            URL url = new URL(htmlPath);
            File file = new File(url.getPath());
            Document document = Jsoup.parse(file, null);
            Elements metaElements = document.select("meta");
            for (Element meta : metaElements) {
                if (meta.hasAttr("name") && meta.attr("name").equals("baker-page-name")) {
                    if (meta.hasAttr("content")) {
                        values.put("baker-page-name", meta.attr("content"));
                    } else {
                        values.put("baker-page-name", "");
                    }
                } else if (meta.hasAttr("name") && meta.attr("name").equals("baker-page-category")) {
                    if (meta.hasAttr("content")) {
                        values.put("baker-page-category", meta.attr("content"));
                    } else {
                        values.put("baker-page-category", "");
                    }
                }
            }
        } catch (IOException e) {
            Log.e(this.getClass().getName(), "Error parsing the document", e);
        }

        return values;
    }

	private void setPagerView(final BookJson book) {

        String path = "file://" + Configuration.getMagazinesDirectory() + File.separator;

        if (STANDALONE_MODE) {
            path = "file:///android_asset".concat(File.separator)
                    .concat(getString(R.string.path_standalone_books_directory)).concat(File.separator);
        } else if (ENABLE_TUTORIAL) {
            path = "file:///android_asset".concat(File.separator);
        }

        if (book.getLiveUrl() != null) {
            path = book.getLiveUrl();
        }
        final String finalPath = path;

        Log.d(this.getClass().toString(), "THE PATH FOR LOADING THE PAGES WILL BE: " + finalPath);

		// ViewPager and its adapters use support library
		// fragments, so use getSupportFragmentManager.
		webViewPagerAdapter = new WebViewFragmentPagerAdapter(
				getSupportFragmentManager(), book, finalPath, this);
		pager = (CustomWebViewPager) findViewById(R.id.pager);
		pager.setAdapter(webViewPagerAdapter);

        //Bind the title indicator to the adapter
        LinePageIndicator indicator = (LinePageIndicator)findViewById(R.id.indicator);
        if (!ENABLE_TUTORIAL) {
            indicator.setVisibility(View.GONE);
        }
        indicator.setViewPager(pager);

        indicator.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                Log.d(this.getClass().getName(), "Loading page at index: " + position);
                detectFirstOrLastPage();

                // Send event to plugins
                String page = finalPath + book.getMagazineName() + File.separator + book.getContents().get(position);
                Map<String, String> tags = getBakerMetaTags(page);
                if (tags.containsKey("baker-page-name")) {
                    String name = tags.get("baker-page-name");
                    name = (name.isEmpty()) ? book.getContents().get(position) : name;
                    BakerApplication.getInstance().getPluginManager().onIssuePageOpened(issue, name, position);
                }

            }
        });

		CustomWebView viewIndex = (CustomWebView) findViewById(R.id.webViewIndex);
		viewIndex.getSettings().setJavaScriptEnabled(true);
		viewIndex.getSettings().setUseWideViewPort(true);
		viewIndex.setWebViewClient(new WebViewClient() {

			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String stringUrl) {

                // mailto links will be handled by the OS.
                if (stringUrl.startsWith("mailto:")) {
                    Uri uri = Uri.parse(stringUrl);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                } else {
                    try {
                        URL url = new URL(stringUrl);

                        // We try to remove the referrer string to avoid passing it to the server in case the URL is an external link.
                        String referrer = "";
                        if (url.getQuery() != null) {
                            Map<String, String> variables = Configuration.splitUrlQueryString(url);
                            String finalQueryString = "";
                            for (Map.Entry<String, String> entry : variables.entrySet()) {
                                if (entry.getKey().equals("referrer")) {
                                    referrer = entry.getValue();
                                } else {
                                    finalQueryString += entry.getKey() + "=" + entry.getValue() + "&";
                                }
                            }
                            if (!finalQueryString.isEmpty()) {
                                finalQueryString = "?" + finalQueryString.substring(0, finalQueryString.length() - 1);
                            }
                            stringUrl = stringUrl.replace("?" + url.getQuery(), finalQueryString);
                        }
                        // Aaaaand that was the process of removing the referrer from the query string.

                        if (!url.getProtocol().equals("file")) {
                            Log.d("REFERRER>>>", "THE REFERRER IS: " + referrer);
                            if (referrer.equals(IssueActivity.this.getString(R.string.url_external_referrer))) {
                                Uri uri = Uri.parse(stringUrl);
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                startActivity(intent);
                            } else if (referrer.equals(IssueActivity.this.getString(R.string.url_baker_referrer))) {
                                IssueActivity.this.openLinkInModal(stringUrl);
                                return true;
                            } else {
                                // We return false to tell the webview that we are not going to handle the URL override.
                                return false;
                            }
                        } else {
                            stringUrl = url.getPath().substring(url.getPath().lastIndexOf("/") + 1);
                            Log.d(">>>URL_DATA", "FINAL INTERNAL HTML FILENAME = " + stringUrl);

                            int index = IssueActivity.this.getJsonBook().getContents().indexOf(stringUrl);

                            if (index != -1) {
                                Log.d(this.getClass().toString(), "Index to load: " + index
                                        + ", page: " + stringUrl);

                                IssueActivity.this.getPager().setCurrentItem(index);
                                view.setVisibility(View.GONE);
                            } else {

                                // If the file DOES NOT exist, we won't load it.

                                File htmlFile = new File(url.getPath());
                                if (htmlFile.exists()) {
                                    return false;
                                }
                            }
                        }
                    } catch (MalformedURLException ex) {
                        Log.d(">>>URL_DATA", ex.getMessage());
                    } catch (UnsupportedEncodingException ignored) {
                    }
                }

				return true;
			}
		});
		viewIndex.loadUrl(path + book.getMagazineName() + File.separator
				+ "index.html");
        viewIndex.setBackgroundColor(0x00000000);
        viewIndex.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {

			WebViewFragment fragment = (WebViewFragment) this.webViewPagerAdapter
					.instantiateItem(pager, pager.getCurrentItem());

			if (fragment.inCustomView()) {
				fragment.hideCustomView();
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
		// Intercept the touch events.
		this.gestureDetector.onTouchEvent(event);

        if (doubleTap) {
            //No need to pass double tap to children
            doubleTap = false;
        } else {
            // We call the superclass implementation for the touch
            // events to continue along children.
            return super.dispatchTouchEvent(event);
        }
        return true;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		this.gestureDetector.onTouchEvent(event);

        if (doubleTap) {
            //No need to pass double tap to children
            doubleTap = false;
        } else {
            // We call the superclass implementation.
            return super.onTouchEvent(event);
        }
        return true;
	}

    public void openLinkInModal(final String url) {
        Intent intent = new Intent(this, ModalActivity.class);
        intent.putExtra(MODAL_URL, url);
        intent.putExtra(ORIENTATION, this.getRequestedOrientation());
        startActivity(intent);
    }

	/**
	 * Used to handle the gestures, but we will only need the onDoubleTap. The
	 * other events will be passed to children views.
	 * 
	 * @author Holland
	 * 
	 */
	class MyGestureListener extends GestureDetector.SimpleOnGestureListener {

		@Override
		public boolean onDoubleTap(MotionEvent event) {
            if (isEnableDoubleTap()) {
                doubleTap = true;
                CustomWebView viewIndex = (CustomWebView) findViewById(R.id.webViewIndex);

                //Disable Index Zoom
                viewIndex.getSettings().setSupportZoom(false);

                if (viewIndex.isShown()) {
                    viewIndex.setVisibility(View.GONE);
                } else {

                    WebViewFragment fragment = (WebViewFragment) IssueActivity.this.webViewPagerAdapter
                            .instantiateItem(pager, pager.getCurrentItem());
                    if (!fragment.inCustomView()) {
                        viewIndex.setVisibility(View.VISIBLE);
                    }
                }
            }
			return true;
		}
	}
}
