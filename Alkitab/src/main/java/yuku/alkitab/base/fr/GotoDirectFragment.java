package yuku.alkitab.base.fr;

import android.app.Activity;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.base.fr.base.BaseGotoFragment;
import yuku.alkitab.base.util.Jumper;
import yuku.alkitab.debug.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GotoDirectFragment extends BaseGotoFragment {
	public static final String TAG = GotoDirectFragment.class.getSimpleName();
	
	private static final String EXTRA_verse = "verse"; //$NON-NLS-1$
	private static final String EXTRA_chapter = "chapter"; //$NON-NLS-1$
	private static final String EXTRA_bookId = "bookId"; //$NON-NLS-1$

	TextView lDirectSample;
	EditText tDirectReference;
	View bOk;

	int bookId;
	int chapter_1;
	int verse_1;
	private Activity activity;


	public static Bundle createArgs(int bookId, int chapter_1, int verse_1) {
		Bundle args = new Bundle();
		args.putInt(EXTRA_bookId, bookId);
		args.putInt(EXTRA_chapter, chapter_1);
		args.putInt(EXTRA_verse, verse_1);
		return args;
	}

	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		this.activity = activity;
	}

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Bundle args = getArguments();
		if (args != null) {
			bookId = args.getInt(EXTRA_bookId, -1);
			chapter_1 = args.getInt(EXTRA_chapter);
			verse_1 = args.getInt(EXTRA_verse);
		}
	}

	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.fragment_goto_direct, container, false);
		lDirectSample = V.get(res, R.id.lDirectSample);
		tDirectReference = V.get(res, R.id.tDirectReference);
		bOk = V.get(res, R.id.bOk);

		bOk.setOnClickListener(bOk_click);
		
		tDirectReference.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				bOk_click.onClick(bOk);
				return true;
			}
		});
		return res;
	}
	
	@Override public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		final String example = S.activeVersion.reference(bookId, chapter_1, verse_1);
		final CharSequence text = getText(R.string.jump_to_prompt);
		SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append(text);
		final CharSequence text2 = TextUtils.expandTemplate(text, example);
		lDirectSample.setText(text2);
	}
	
	View.OnClickListener bOk_click = new View.OnClickListener() {
		public Pattern nobookPattern;

		@Override public void onClick(View v) {
			String reference = tDirectReference.getText().toString();
			
			if (reference.trim().length() == 0) {
				return; // do nothing
			}

			// typing chapter or chapter:verse was broken sometime. Let us make a special case to handle this.
			if (nobookPattern == null) {
				nobookPattern = Pattern.compile("(\\d+)(?:[ :.]+(\\d+))?");
			}

			final Matcher m = nobookPattern.matcher(reference.trim());
			if (m.matches()) {
				try {
					final String chapter_s = m.group(1);
					final int chapter_1 = Integer.parseInt(chapter_s);

					final String verse_s = m.group(2);
					final int verse_1;
					if (verse_s != null) {
						verse_1 = Integer.parseInt(verse_s);
					} else {
						verse_1 = 0;
					}

					((GotoFinishListener) activity).onGotoFinished(GotoFinishListener.GOTO_TAB_direct, bookId, chapter_1, verse_1);
					return;
				} catch (NumberFormatException ignored) {}
			}

			final Jumper jumper = new Jumper(reference);
			if (! jumper.getParseSucceeded()) {
				new AlertDialogWrapper.Builder(getActivity())
					.setMessage(getString(R.string.alamat_tidak_sah_alamat, reference))
					.setPositiveButton(R.string.ok, null)
					.show();
				return;
			}
			
			final int bookId = jumper.getBookId(S.activeVersion.getConsecutiveBooks());
			final int chapter = jumper.getChapter();
			final int verse = jumper.getVerse();

			((GotoFinishListener) activity).onGotoFinished(GotoFinishListener.GOTO_TAB_direct, bookId, chapter, verse);
		}
	};
}
