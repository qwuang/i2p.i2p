package net.i2p.i2ptunnel.access;

import java.util.List;
import java.util.ArrayList;

import java.io.File;

import net.i2p.data.DataHelper;

/**
 * Utility class for parsing filter definitions
 * 
 * @since 0.9.40
 */
class DefinitionParser {

    /**
     * Processes an array of String objects containing the human-readable definition of
     * the filter.
     *
     * The definition of a filter is a list of Strings.  Each line can represent one of 
     * these items:
     *
     * * definition of a default threshold to apply to any remote destinations not
     *   listed in this file or any of the referenced files
     * * definition of a threshold to apply to a specific remote destination
     * * definition of a threshold to apply to remote destinations listed in a file
     * * definition of a threshold that if breached will cause the offending remote
     *   destination to be recorded in a specified file
     *
     * The order of the definitions matters.  The first threshold for a given destination
     * (whether explicit or listed in a file) overrides any future thresholds for the
     * same destination, whether explicit or listed in a file.
     *
     * Thresholds:
     * 
     * A threshold is defined by the number of connection attempts a remote destination is
     * permitted to perform over a specified number of minutes before a "breach" occurs.
     * For example the following threshold definition "15/5" means that the same remote
     * destination is allowed to make 14 connection attempts over a 5 minute period,  If
     * it makes one more attempt within the same period, the threshold will be breached.
     *
     * The threshold format can be one of the following:
     *
     * * Numeric definition of number of connections over number minutes - "15/5", 
     *   "30/60", and so on.  Note that if the number of connections is 1 (as for 
     *   example in "1/1") the first connection attempt will result in a breach.
     * * The word "allow".  This threshold is never breached, i.e. infinite number of 
     *   connection attempts is permitted.
     * * The word "deny".  This threshold is always breached, i.e. no connection attempts
     *   will be allowed.
     *
     * Default threshold
     *
     * The default threshold applies to any remote destinations that are not explicitly
     * listed in the definition or in any of the referenced files.  To set a default 
     * threshold use the keyword "default".  The following are examples of default thresholds:
     * 
     * -----------------------------
     * default 15/5
     * default allow
     * default deny
     * -----------------------------
     *
     * Explicit thresholds
     * 
     * Explicit thresholds are applied to a remote destination listed in the definition itself.
     * Examples:
     * 
     * -----------------------------
     * 15/5 explicit asdfasdfasdf.b32.i2p
     * allow explicit fdsafdsafdsa.b32.i2p
     * deny explicit qwerqwerqwer.b32.i2p
     * -----------------------------
     *
     * Thresholds for destinations listed in a file
     * 
     * For convenience it is possible to maintain a list of destinations in a file and define
     * a threshold for all of them in bulk.  Examples:
     *
     * -----------------------------
     * 15/5 file /path/throttled_destinations.txt
     * deny file /path/forbidden_destinations.txt
     * allow file /path/unlimited_destinations.txt
     * -----------------------------
     *
     * Recorders
     *
     * Recorders keep track of connection attempts made by a remote destination, and if that
     * breaches a certain threshold, that destination gets recorded in a given file.  Examples:
     *
     * -----------------------------
     * recorder 30/5 /path/aggressive.txt
     * recorder 60/5 /path/very_aggressive.txt
     * -----------------------------
     *
     * It is possible to use a recorder to record aggressive destinations to a given file,
     * and then use that same file to throttle them.  For example, the following snippet will
     * define a filter that initially allows all connection attempts, but if any single
     * destination exceeds 30 attempts per 5 minutes it gets throttled down to 15 attempts per 
     * 5 minutes:
     *
     * -----------------------------
     * # by default there are no limits
     * default allow
     * # but record overly aggressive destinations
     * recorder 30/5 /path/throttled.txt
     * # and any that end up in that file will get throttled in the future
     * 15/5 file /path/throttled.txt
     * -----------------------------
     *
     * It is possible to use a recorder in one tunnel that writes to a file that throttles 
     * another tunnel.  It is possible to reuse the same file with destinations in multiple
     * tunnels.  And of course, it is possible to edit these files by hand.
     *
     * Here is an example filter definition that applies some throttling by default, no throttling
     * for destinations in the file "friends.txt", forbids any connections from destinations
     * in the file "enemies.txt" and records any aggressive behavior in a file called
     * "suspicious.txt":
     *
     * -----------------------------
     * default 15/5
     * allow file /path/friends.txt
     * deny file /path/enemies.txt
     * recorder 60/5 /path/suspicious.txt
     * -----------------------------
     *
     * @return a FilterDefinition POJO representation for internal use
     * @throws InvalidDefinitionException if the definition is malformed
     */
    static FilterDefinition parse(String []definition) throws InvalidDefinitionException {
        
        DefinitionBuilder builder = new DefinitionBuilder();

        for (String line : definition) {
            String [] split = DataHelper.split(line," ");
            split[0] = split[0].toLowerCase();
            if ("default".equals(split[0])) 
                builder.setDefaultThreshold(parseThreshold(line.substring(7).trim()));
            else if ("recorder".equals(split[0]))
                builder.addRecorder(parseRecorder(line.substring(8).trim()));
            else
                builder.addElement(parseElement(line));
        }

        return builder.build();
    }

    private static Threshold parseThreshold(String s) throws InvalidDefinitionException {
        if ("allow".equals(s))
            return Threshold.ALLOW;
        if ("deny".equals(s))
            return Threshold.DENY;

        String [] split = DataHelper.split(s,"/");
        if (split.length != 2)
            throw new InvalidDefinitionException("Invalid threshold " + s);

        try {
            int connections = Integer.parseInt(split[0]);
            int minutes = Integer.parseInt(split[1]);
            if (connections < 0)
                throw new InvalidDefinitionException("Number of connections cannot be negative " + s);
            if (minutes < 1)
                throw new InvalidDefinitionException("Number of minutes must be at least 1 " + s);
            return new Threshold(connections, minutes);
        } catch (NumberFormatException bad) {
            throw new InvalidDefinitionException("Invalid threshold", bad);
        }
    }

    private static Recorder parseRecorder(String line) throws InvalidDefinitionException {
        String thresholdString = extractThreshold(line);

        Threshold threshold = parseThreshold(thresholdString);

        String line2 = line.substring(thresholdString.length()).trim();
        if (line2.length() == 0)
            throw new InvalidDefinitionException("Invalid definition "+ line);
        File file = new File(line2);
        return new Recorder(file, threshold);
    }

    private static String extractThreshold(String line) {
        StringBuilder thresholdBuilder = new StringBuilder();
        int i = 0;
        while(i < line.length()) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t')
                break;
            i++;
            thresholdBuilder.append(c);
        }
        return thresholdBuilder.toString();
    }

    private static FilterDefinitionElement parseElement(String line) throws InvalidDefinitionException {
        String thresholdString = extractThreshold(line);
        Threshold threshold = parseThreshold(thresholdString);
        String line2 = line.substring(thresholdString.length()).trim();
        String[] split = DataHelper.split(line2," ");
        if (split.length < 2)
            throw new InvalidDefinitionException("invalid definition "+line);
        if ("explicit".equals(split[0]))
            return new ExplicitFilterDefinitionElement(split[1], threshold);
        if ("file".equals(split[0])) {
            String line3 = line2.substring(4).trim();
            File file = new File(line3);
            return new FileFilterDefinitionElement(file, threshold);
        }
        throw new InvalidDefinitionException("invalid definition "+line);
    }

    private static class DefinitionBuilder {
        private Threshold threshold;
        private List<FilterDefinitionElement> elements = new ArrayList<FilterDefinitionElement>();
        private List<Recorder> recorders = new ArrayList<Recorder>();

        void setDefaultThreshold(Threshold threshold) throws InvalidDefinitionException {
            if (this.threshold != null)
                throw new InvalidDefinitionException("default already set!");
            this.threshold = threshold;
        }

        void addElement(FilterDefinitionElement element) {
            elements.add(element);
        }

        void addRecorder(Recorder recorder) {
            recorders.add(recorder);
        }

        FilterDefinition build() {
            if (threshold == null)
                threshold = Threshold.ALLOW;

            FilterDefinitionElement [] elArray = new FilterDefinitionElement[elements.size()];
            elArray = elements.toArray(elArray);

            Recorder [] rArray = new Recorder[recorders.size()];
            rArray = recorders.toArray(rArray);

            return new FilterDefinition(threshold, elArray, rArray);
        }
    }
}
