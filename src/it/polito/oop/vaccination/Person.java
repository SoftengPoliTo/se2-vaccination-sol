package it.polito.oop.vaccination;

class Person {
	private String cf;
	private String last;
	private String first;
	private int year;
	private boolean allocated;
	
	Person(String cf, String last, String first, int year) {
		this.cf = cf;
		this.last = last;
		this.first = first;
		this.year = year;
	}

	public int getYear() {
		return year;
	}

	public int getAge() {
		return Vaccines.CURRENT_YEAR-year;
	}
	
	public String getSSN() {
		return cf;
	}

	public boolean available() {return !allocated; }
	public boolean allocated() {return allocated; }
	void setAllocated() { allocated=true; }

	public void clearAllocated() {
		allocated = false;
	}

	@Override
	public String toString() {
		return cf+","+last+","+first;
	}
}
