package it.polito.oop.vaccination;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

class Hub {
	private static final int DOCTOR_CAPACITY = 10;
	private static final int NURSES_CAPACITY = 12;
	private static final int OTHER_CAPACITY = 20;
	private String name;
	private int doctors=-1;
	private int nurses=-1;
	private int other=-1;
	
	Hub(String name){
		this.name=name;
	}

	public void setStaff(int doctors, int nurses, int other) {
		this.doctors = doctors;
		this.nurses = nurses;
		this.other = other;
	}
	
	public String getName() { return name; }
	
	public List<Integer> availability(int[] hours) throws VaccineException{
		int c = estimateCapacity();
		return Arrays.stream(hours).mapToObj( h -> c*h )
				.collect(Collectors.toList());
	}
	
	public int estimateCapacity() throws VaccineException {
		if(doctors==-1) throw  new VaccineException("Cannot estimate capacity without staff in hub " + name);
		return Math.min( doctors * DOCTOR_CAPACITY, Math.min(nurses*NURSES_CAPACITY, other*OTHER_CAPACITY));
	}
}
