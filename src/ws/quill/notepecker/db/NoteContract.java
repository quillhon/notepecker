package ws.quill.notepecker.db;

import android.net.Uri;
import android.provider.BaseColumns;

public class NoteContract {
	
	private static final String TAG = NoteContract.class.getSimpleName();
	
	public static final String CONTENT_AUTHORITY = "ws.quill.notepeaker";
	public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);
	
	private static final String PATH_BOOKS = "books";
	
	interface BooksColumns {
		String BOOK_ISBN = "book_isbn";
		String BOOK_NAME = "book_name";
	}
	
	public static class Books implements BooksColumns, BaseColumns {
		public static final Uri CONTENT_URI =
                BASE_CONTENT_URI.buildUpon().appendPath(PATH_BOOKS).build();
		
		public static final String DEFAULT_SORT = BooksColumns.BOOK_NAME + " ASC";
		
		public static Uri buildBookUri(String bookId) {
			return CONTENT_URI.buildUpon().appendPath(bookId).build();
		}
		
	}

	public NoteContract() {
	}

}