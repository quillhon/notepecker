package ws.quill.notepecker.view;

import java.util.ArrayList;

import ws.quill.notepecker.R;
import ws.quill.notepecker.model.Note;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;

public class NoteOverylay {

	private ArrayList<Note> notes;

	public NoteOverylay(Context context) {
	}

	public ArrayList<Note> getNotes() {
		return notes;
	}

	public void setNotes(ArrayList<Note> notes) {
		this.notes = notes;
	}

	public void draw(Canvas c) {
		Paint paint = new Paint();
		paint.setColor(Color.RED);
		paint.setTextSize(80);
		paint.setAntiAlias(true);
		
		for (Note note : notes) {
			c.drawText(note.getText(), note.getX(), note.getY(), paint);
		}
	}
}
