package it.polito.oop.vaccination;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Vaccines {
	public final static int CURRENT_YEAR = java.time.LocalDate.now().getYear();

	private final static double PRIORITY = 0.4;

	// fields

	private Map<String, Person> persons = new HashMap<>();
	private Map<String, Interval> intervals = new HashMap<>();
	private Map<String, Hub> hubs = new HashMap<>();
	private int[] hours;
	private int allocated;
	private List<Map<String, List<String>>> weekPlan;
	private BiConsumer<Integer, String> listener = null;

// R1

	/**
	 * Add a new person to the vaccination system.
	 * 
	 * Persons are uniquely identified by SSN (italian "codice fiscale")
	 * 
	 * @param first first name
	 * @param last  last name
	 * @param ssn   italian "codice fiscale"
	 * @param year  birth year
	 * @return {@code false} if ssn is duplicate,
	 */
	public boolean addPerson(String first, String last, String ssn, int year) {
		if (persons.containsKey(ssn))
			return false;
		Person p = new Person(ssn, last, first, year);
		persons.put(ssn, p);
		if (!intervals.isEmpty()) {
			intervals.values().stream().filter(i -> i.match(p)).forEach(i -> i.addPerson(p));
		}
		return true;
	}

	/**
	 * Count the number of people added to the system
	 * 
	 * @return person count
	 */
	public int countPeople() {
		return persons.size();
	}

	/**
	 * Retrieves information about a person. Information is formatted as ssn, last
	 * name, and first name separate by {@code ','} (comma).
	 * 
	 * @param ssn "codice fiscale" of person searched
	 * @return info about the person
	 */
	public String getPerson(String ssn) {
		Person person = persons.get(ssn);
		return person == null ? null : person.toString();
	}

	/**
	 * Retrieves of a person given their SSN (codice fiscale).
	 * 
	 * @param ssn "codice fiscale" of person searched
	 * @return age of person (in years)
	 */
	public int getAge(String ssn) {
		Person person = persons.get(ssn);
		return person == null ? -1 : person.getAge();
	}

	/**
	 * Define the age intervals by providing the breaks between intervals. The first
	 * interval always start at 0 (non included in the breaks) and the last interval
	 * goes until infinity (not included in the breaks). All intervals are closed on
	 * the lower boundary and open at the upper one.
	 * <p>
	 * For instance {@code setAgeIntervals(40,50,60)} defines four intervals
	 * {@code "[0,40)", "[40,50)", "[50,60)", "[60,+)"}.
	 * 
	 * @param breaks the array of breaks
	 */
	public void setAgeIntervals(int... breaks) {
		intervals.clear();
		int prev = 0;
		for (int b : breaks) {
			Interval i = new Interval(prev, b);
			intervals.put(i.toString(), i);
			prev = b;
		}
		Interval i = new Interval(prev, Integer.MAX_VALUE);
		intervals.put(i.toString(), i);

	}

	/**
	 * Retrieves the labels of the age intervals defined.
	 * 
	 * Interval labels are formatted as {@code "[0,10)"}, if the upper limit is
	 * infinity {@code '+'} is used instead of the number.
	 * 
	 * @return labels of the age intervals
	 */
	public Collection<String> getAgeIntervals() {
		return Collections.unmodifiableCollection(intervals.keySet());
	}

	/**
	 * Retrieves people in the given interval.
	 * 
	 * The age of the person is computed by subtracting the birth year from current
	 * year.
	 * 
	 * @param interval age interval label
	 * @return collection of SSN of person in the age interval
	 */
	public Collection<String> getInInterval(String interval) {
		Interval i = intervals.get(interval);
		if (i == null)
			return new ArrayList<>();
		return persons.values().stream().filter(i::match).map(Person::getSSN).collect(Collectors.toList());
		// ALTERNATIVELY if interval keep list of persons
//		return i.getPersons().stream().map(Person::getSSN).collect(Collectors.toList());
	}

	// R2
	/**
	 * Define a vaccination hub
	 * 
	 * @param name name of the hub
	 * @throws VaccineException in case of duplicate name
	 */
	public void defineHub(String name) throws VaccineException {
		if (hubs.containsKey(name)) {
			throw new VaccineException("Duplicate hub name: " + name);
		}
		Hub h = new Hub(name);
		hubs.put(name, h);
	}

	/**
	 * Retrieves hub names
	 * 
	 * @return hub names
	 */
	public Collection<String> getHubs() {
		return hubs.keySet();
	}

	/**
	 * Define the staffing of a hub in terms of doctors, nurses and other personnel.
	 * 
	 * @param name    name of the hub
	 * @param doctors number of doctors
	 * @param nurses  number of nurses
	 * @param other   number of other personnel
	 * 
	 * @throws VaccineException in case of undefined hub, or any number of personnel
	 *                          not greater than 0.
	 */
	public void setStaff(String name, int doctors, int nurses, int other) throws VaccineException {
		Hub h = hubs.get(name);
		if (h == null)
			throw new VaccineException("No hub named " + name);
		if (doctors <= 0 || nurses <= 0 || other <= 0)
			throw new VaccineException("Staff must be >0");
		h.setStaff(doctors, nurses, other);
	}

	/**
	 * Estimates the hourly vaccination capacity of a hub
	 * 
	 * The capacity is computed as the minimum among 10*number_doctor,
	 * 12*number_nurses, 20*number_other
	 * 
	 * @param hubName name of the hub
	 * @return hourly vaccination capacity
	 * 
	 * @throws VaccineException in case of undefined or hub without staff
	 */
	public int estimateHourlyCapacity(String hubName) throws VaccineException {
		Hub h = hubs.get(hubName);
		if (h == null)
			throw new VaccineException("No hub named " + hubName);

		return h.estimateCapacity();
	}

	// R3
	/**
	 * Load people information stored in CSV format.
	 * 
	 * The header must start with {@code "SSN,LAST,FIRST"}. All lines must have at
	 * least three elements.
	 * 
	 * In case of error in a person line the line is skipped.
	 * 
	 * @param people {@code Reader} for the CSV content
	 * @return number of correctly added people
	 * @throws IOException      in case of IO error
	 * @throws VaccineException in case of error in the header
	 */
	public long loadPeople(Reader people) throws IOException, VaccineException {
		BufferedReader br = new BufferedReader(people);
		String header = br.readLine();
		if (!header.matches("SSN *, *LAST *, *FIRST *, *YEAR.*")) {
			if (listener != null)
				listener.accept(1, header);
			throw new VaccineException("Wrong header in people file: " + header);
		}
		long[] count = { 0 };
		int[] lineNo = { 1 };
		br.lines().forEach(l -> {
			lineNo[0]++;
			String[] fields = l.split(" *, *");
			if (fields.length < 4) {
				if (listener != null)
					listener.accept(lineNo[0], l);
			} else if (addPerson(fields[2], fields[1], fields[0], Integer.parseInt(fields[3])))
				count[0]++;
			else if (listener != null)
				listener.accept(lineNo[0], l);
		});
		return count[0];
	}

	// R4
	/**
	 * Define the amount of working hours for the days of the week.
	 * 
	 * Exactly 7 elements are expected, where the first one correspond to Monday.
	 * 
	 * @param hours workings hours for the 7 days.
	 * @throws VaccineException if there are not exactly 7 elements or if the sum of
	 *                          all hours is less than 0 ore greater than 24*7.
	 */
	public void setHours(int... hours) throws VaccineException {
		if (hours.length != 7)
			throw new VaccineException("There must be exactly 7 hours elements");
		for (int h : hours)
			if (h < 0 || h > 12)
				throw new VaccineException("Wrong hours: " + h);
		this.hours = hours;
	}

	/**
	 * Returns the list of standard time slots for all the days of the week.
	 * 
	 * Time slots start at 9:00 and occur every 15 minutes (4 per hour) and they
	 * cover the number of working hours defined through method {@link #setHours}.
	 * <p>
	 * Times are formatted as {@code "09:00"} with both minuts and hours on two
	 * digits filled with leading 0.
	 * <p>
	 * Returns a list with 7 elements, each with the time slots of the corresponding
	 * day of the week.
	 * 
	 * @return the list hours for each day of the week
	 */
	public List<List<String>> getHours() {
		return Arrays.stream(hours).mapToObj(Integer::valueOf).map(h -> IntStream.range(0, 4 * h)
				.mapToObj(x -> String.format("%02d:%02d", (9 + x / 4), 15 * (x % 4))).collect(Collectors.toList()))
				.collect(Collectors.toList());
	}

	/**
	 * Compute the available vaccination slots for a given hub on a given day of the
	 * week
	 * <p>
	 * The availability is computed as the number of working hours of that day
	 * multiplied by the hourly capacity (see {@link #estimateCapacity} of the hub.
	 * 
	 * @return
	 */
	public int getDailyAvailable(String hubName, int day) {
		Hub h = hubs.get(hubName);

		try {
			return h.estimateCapacity() * hours[day];
		} catch (Exception e) {
			System.err.println(e);
			return -1;
		}
	}

	/**
	 * Compute the available vaccination slots for each hub and for each day of the
	 * week
	 * <p>
	 * The method returns a map that associates the hub names (keys) to the lists of
	 * number of available hours for the 7 days.
	 * <p>
	 * The availability is computed as the number of working hours of that day
	 * multiplied by the capacity (see {@link #estimateCapacity} of the hub.
	 * 
	 * @return
	 */
	public Map<String, List<Integer>> getAvailable() {
		return hubs.values().stream().collect(Collectors.toMap(Hub::getName, h -> {
			try {
				return h.availability(hours);
			} catch (VaccineException e) {
				return new ArrayList<>();
			}
		}));
	}

	/**
	 * Computes the general allocation plan a hub on a given day. Starting with the
	 * oldest age intervals 40% of available places are allocated to persons in that
	 * interval before moving the the next interval and considering the remaining
	 * places.
	 * <p>
	 * The returned value is the list of SSNs (codice fiscale) of the persons
	 * allocated to that day
	 * <p>
	 * <b>N.B.</b> no particular order of allocation is guaranteed
	 * 
	 * @param hubName name of the hub
	 * @param day     day of week index (0 = Monday)
	 * @return the list of daily allocations
	 */
	public List<String> allocate(String hubName, int day) {
		int n = getDailyAvailable(hubName, day);
		List<String> res = new ArrayList<>(n);

		// System.out.println("Fase 1 (n=" + n +")");
		List<String> ints = intervals.values().stream().sorted(Comparator.reverseOrder()).map(Interval::toString)
				.collect(Collectors.toList());
		for (String interval : ints) { // add using priority
			int a = res.size();
			int quota = (int) ((n - res.size()) * PRIORITY);
			// System.out.println(interval + " -> " + quota + " (" + ( quota/(double)n )
			// +"%)");
			getInInterval(interval).stream().map(persons::get).filter(Person::available).limit(quota).forEach(p -> {
				p.setAllocated();
				res.add(p.getSSN());
			});
			if (quota != res.size() - a) {
				System.out.println("Could not find " + quota + " from interval " + interval);
			}
		}
		// System.out.println("Fase 2");
		for (String interval : ints) { // complete
			int quota = n - res.size();
			if (quota == 0)
				break;
			// System.out.println(interval + " -> " + quota + " (" + ( quota/(double)n )
			// +"%)");
			getInInterval(interval).stream().map(persons::get).filter(Person::available).limit(quota).forEach(p -> {
				p.setAllocated();
				res.add(p.getSSN());
			});
		}

		// OR using the pre-associated list in intervals

//		List<Interval> ints = intervals.values().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
//		for(Interval interval : ints) { // add using priority
//			interval.getPersons().stream()
//			.filter(Person::available)
//			.limit((int)((n-res.size())*PRIORITY))
//			.forEach( p -> {
//				p.setAllocated();
//				res.add(p.getSSN());
//			});
//		}
//		for(Interval interval : ints) { // add using priority
//			int quota = n-res.size();
//			if(quota==0) break;
//			interval.getPersons().stream()
//			.filter(Person::available)
//			.limit(quota)
//			.forEach( p -> {
//				p.setAllocated();
//				res.add(p.getSSN());
//			});
//		}
		return res;
	}

	/**
	 * Removes all people from allocation lists and clears their allocation status
	 * 
	 */
	public void clearAllocation() {
		persons.forEach((ssn, p) -> p.clearAllocated());
	}

	/**
	 * Computes the general allocation plan for the week. For every day, starting
	 * with the oldest age intervals 40% available places are allocated to persons
	 * in that interval before moving the the next interval and considering the
	 * remaining places.
	 * <p>
	 * The returned value is a list with 7 elements, one for every day of the week,
	 * each element is a map that links the name of each hub to the list of SSNs
	 * (codice fiscale) of the persons allocated to that day in that hub
	 * <p>
	 * <b>N.B.</b> no particular order of allocation is guaranteed but the same
	 * invocation (after {@link #clearAllocation}) must return the same allocation.
	 * 
	 * @return the list of daily allocations
	 */
	public List<Map<String, List<String>>> weekAllocate() {
		weekPlan = IntStream.range(0, 7)
				.mapToObj(d -> hubs.keySet().stream()
						.collect(Collectors.toMap(hubName -> hubName, hubName -> allocate(hubName, d))))
				.collect(Collectors.toList());

		allocated = weekPlan.stream().flatMapToInt(m -> m.values().stream().mapToInt(List::size)).sum();

		return weekPlan;
	}

	// R5
	/**
	 * Returns the proportion of allocated people w.r.t. the total number of persons
	 * added in the system
	 * 
	 * @return proportion of allocated people
	 */
	public double propAllocated() {
		return persons.values().stream().filter(Person::allocated).count() / (double) persons.size();
	}

	/**
	 * Returns the proportion of allocated people w.r.t. the total number of persons
	 * added in the system, divided by age interval.
	 * <p>
	 * The map associates the age interval label to the proportion of allocates
	 * people in that interval
	 * 
	 * @return proportion of allocated people by age interval
	 */
	public Map<String, Double> propAllocatedAge() {
//		return persons.values().stream()
//				.filter(Person::allocated)
//				.collect(Collectors.groupingBy( 
//							this::personToInterval,
//							Collectors.collectingAndThen(Collectors.counting(),
//														 cnt -> cnt / (double)persons.size()
//						)));

		Map<String, Double> res = persons.values().stream().filter(Person::allocated).collect(Collectors.groupingBy(
				this::personToInterval, Collectors.collectingAndThen(Collectors.counting(), cnt -> (double) cnt)));
		res.replaceAll((i, c) -> c / getInInterval(i).size());
		return res;

	}

	/**
	 * Retrieves the distribution of allocated persons among the different age
	 * intervals.
	 * <p>
	 * For each age intervals the map reports the proportion of allocated persons in
	 * the corresponding interval w.r.t the total number of allocated persons
	 * 
	 * @return
	 */
	public Map<String, Double> distributionAllocated() {
		return persons.values().stream().filter(Person::allocated).collect(Collectors.groupingBy(this::personToInterval,
				Collectors.collectingAndThen(Collectors.counting(), cnt -> cnt / (double) allocated)));
	}

	// R6
	/**
	 * Defines a listener for the file loading method. The {@ accept()} method of
	 * the listener is called passing the line number and the offending line.
	 * <p>
	 * Lines start at 1 with the header line.
	 * 
	 * @param listener the listener for load errors
	 */
	public void setLoadListener(BiConsumer<Integer, String> listener) {
		this.listener = listener;
	}

	// R7
	/**
	 * Computes the details schedule for the week as a further refinement of the
	 * general week plan
	 * 
	 * @return the weekly schedule
	 */
	public List<Map<String, Map<String, List<String>>>> detailedSchedule() {
		return null;
	}

	/// ---- UTILITY METHODS -----
	static int ssnToYear(String cf) {
		int year = Integer.parseInt(cf.substring(6, 8));
		if (year <= Vaccines.CURRENT_YEAR % 100)
			year += 2000;
		else
			year += 1900;
		return year;
	}

	String personToInterval(Person p) {
		return intervals.entrySet().stream().filter(e -> e.getValue().match(p.getAge())).map(e -> e.getKey())
				.findFirst().get();
	}

	private class Interval implements Comparable<Interval> {
		int lower, upper;
		List<Person> people;

		Interval(int l, int u) {
			this.lower = l;
			this.upper = u;

			// optional feature: pre-memorization of people in interval
			people = persons.values().stream().filter(this::match).collect(Collectors.toList());
		}

		public void addPerson(Person p) {
			if (people != null)
				people.add(p);
		}

		@Override
		public String toString() {
			return "[" + lower + "," + (upper == Integer.MAX_VALUE ? "+" : upper) + ")";
		}

		boolean match(Person p) {
			return match(p.getAge());
		}

		boolean match(int age) {
			return age >= lower && age < upper;
		}

		@Override
		public int compareTo(Interval other) {
			return this.lower - other.lower;
		}
	}

}
