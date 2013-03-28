package ws.quill.notepecker.db;

import android.net.Uri;
import android.provider.BaseColumns;

public class DatabaseContract {
	
	public static class Books implements BaseColumns {
		public static final String TABLE_NAME = "books";
		public static final String COLUMN_ID = "id";
		public static final String CLOUMN_NAME= "name";
		public static final String CLOUMN_ISBN = "isbn";
		
		private Books(){
		}
	}
	
	public static class Notes implements BaseColumns {
		public static final String TABLE_NAME = "notes";
		public static final String COLUMN_ID = "id";
		public static final String COLUMN_X = "x";
		public static final String COLUMN_Y = "y";
		public static final String COLUMN_BOOK_ID = "book_id";
		public static final String CLOUMN_TEXT = "text";
		
		private Notes(){
		}
	}
	
	private DatabaseContract() {
	}

}