package ws.quill.notepecker.db;

import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHandler extends SQLiteOpenHelper {
	
	private static final int DATABASE_VERSION = 1;
	 
    // Database Name
    private static final String DATABASE_NAME = "notePeaker.db";

	public DatabaseHandler(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		String CREATE_BOOKS_TABLE = 
				"CREATE TABLE " + DatabaseContract.Books.TABLE_NAME + "(" + 
                DatabaseContract.Books.COLUMN_ID + " INTEGER PRIMARY KEY," +
                DatabaseContract.Books.CLOUMN_NAME + " TEXT NOT NULL," +
                DatabaseContract.Books.CLOUMN_ISBN + " TEXT NOT NULL);";
        db.execSQL(CREATE_BOOKS_TABLE);
        
		String CREATE_NOTES_TABLE = 
				"CREATE TABLE " + DatabaseContract.Notes.TABLE_NAME + "(" + 
                DatabaseContract.Notes.COLUMN_ID + " INTEGER PRIMARY KEY," +
                DatabaseContract.Notes.COLUMN_BOOK_ID + " INTEGER NOT NULL," +
                DatabaseContract.Notes.CLOUMN_TEXT + " TEXT);";
        db.execSQL(CREATE_NOTES_TABLE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		String DELETE_BOOKS_TABLE = "DROP TABLE IF EXISTS " + DatabaseContract.Books.TABLE_NAME;
		String DELETE_NOTES_TABLE = "DROP TABLE IF EXISTS " + DatabaseContract.Notes.TABLE_NAME;
		db.execSQL(DELETE_BOOKS_TABLE);
		db.execSQL(DELETE_NOTES_TABLE);
        onCreate(db);
	}

}
