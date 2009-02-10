// AccessTracker_p.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.01.2007 on http://www.yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import de.anomic.http.httpRequestHeader;
import de.anomic.kelondro.order.DateFormatter;
import de.anomic.net.natLib;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeed;

public class AccessTracker_p {
    
	private static final SortedMap<Long, String> treemapclone(final SortedMap<Long, String> m) {
		final TreeMap<Long, String> accessClone = new TreeMap<Long, String>();
		try {
			accessClone.putAll(m);
		} catch (final ConcurrentModificationException e) {}
		return accessClone;
	}
	
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
     
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        prop.setLocalized(!(header.get("PATH")).endsWith(".xml"));
        int page = 0;
        if (post != null) page = post.getInt("page", 0);
        prop.put("page", page);
     
        final int maxCount = 1000;
        boolean dark = true;
        if (page == 0) {
            final Iterator<String> i = sb.accessHosts();
            String host;
            SortedMap<Long, String> access;
            int entCount = 0;
            try {
            while ((entCount < maxCount) && (i.hasNext())) {
                host = i.next();
                access = sb.accessTrack(host);
                prop.putHTML("page_list_" + entCount + "_host", host);
                prop.putNum("page_list_" + entCount + "_countSecond", access.tailMap(Long.valueOf(System.currentTimeMillis() - 1000)).size());
                prop.putNum("page_list_" + entCount + "_countMinute", access.tailMap(Long.valueOf(System.currentTimeMillis() - 1000 * 60)).size());
                prop.putNum("page_list_" + entCount + "_count10Minutes", access.tailMap(Long.valueOf(System.currentTimeMillis() - 1000 * 60 * 10)).size());
                prop.putNum("page_list_" + entCount + "_countHour", access.tailMap(Long.valueOf(System.currentTimeMillis() - 1000 * 60 * 60)).size());
                entCount++;
            }
            } catch (final ConcurrentModificationException e) {} // we don't want to synchronize this
            prop.put("page_list", entCount);
            prop.put("page_num", entCount);
            
            entCount = 0;
            try {
            for (final Map.Entry<String, Integer> bfe: serverCore.bfHost.entrySet()) {
                prop.putHTML("page_bflist_" + entCount + "_host", bfe.getKey());
                prop.putNum("page_bflist_" + entCount + "_countSecond", bfe.getValue());
                entCount++;
            }
            } catch (final ConcurrentModificationException e) {} // we dont want to synchronize this
            prop.put("page_bflist", entCount);
        }
        if (page == 1) {
            String host = (post == null) ? "" : post.get("host", "");
            int entCount = 0;
            SortedMap<Long, String> access;
            Map.Entry<Long, String> entry;
            if (host.length() > 0) {
				access = sb.accessTrack(host);
				if (access != null) {
					try {
						final Iterator<Map.Entry<Long, String>> ii = treemapclone(access).entrySet().iterator();
						while (ii.hasNext()) {
							entry = ii.next();
							prop.putHTML("page_list_" + entCount + "_host", host);
							prop.put("page_list_" + entCount + "_date", DateFormatter.formatShortSecond(new Date((entry.getKey()).longValue())));
							prop.putHTML("page_list_" + entCount + "_path", entry.getValue());
							entCount++;
						}
					} catch (final ConcurrentModificationException e) {} // we don't want to synchronize this
				}
			} else {
                try {
                	final Iterator<String> i = sb.accessHosts();
                    while ((entCount < maxCount) && (i.hasNext())) {
						host = i.next();
						access = sb.accessTrack(host);
						final Iterator<Map.Entry<Long, String>> ii = treemapclone(access).entrySet().iterator();
						while (ii.hasNext()) {
							entry = ii.next();
							prop.putHTML("page_list_" + entCount + "_host", host);
							prop.put("page_list_" + entCount + "_date", DateFormatter.formatShortSecond(new Date((entry.getKey()).longValue())));
							prop.putHTML("page_list_" + entCount + "_path", entry.getValue());
							entCount++;
						}
					}
				} catch (final ConcurrentModificationException e) {} // we dont want to synchronize this
			}
            prop.put("page_list", entCount);
            prop.put("page_num", entCount);
        }
        if ((page == 2) || (page == 4)) {
            final ArrayList<plasmaSearchQuery> array = (page == 2) ? sb.localSearches : sb.remoteSearches;
            plasmaSearchQuery searchProfile;
            int m = Math.min(maxCount, array.size());
            long qcountSum = 0;
            long rcountSum = 0;
            long rcount = 0;
            long utimeSum = 0;
            long stimeSum = 0;
            long rtimeSum = 0;
            long utimeSum1 = 0;
            long stimeSum1 = 0;
            long rtimeSum1 = 0;
            
            for (int entCount = 0; entCount < m; entCount++) {
                searchProfile = array.get(array.size() - entCount - 1);
            
                // put values in template
                prop.put("page_list_" + entCount + "_dark", ((dark) ? 1 : 0) );
                dark =! dark;
                prop.putHTML("page_list_" + entCount + "_host", searchProfile.host);
                prop.put("page_list_" + entCount + "_date", DateFormatter.formatShortSecond(new Date(searchProfile.handle.longValue())));
                prop.put("page_list_" + entCount + "_timestamp", searchProfile.handle.longValue());
                if (page == 2) {
                    // local search
                    prop.putNum("page_list_" + entCount + "_offset", searchProfile.offset);
                    prop.putHTML("page_list_" + entCount + "_querystring", searchProfile.queryString);
                } else {
                    // remote search
                    prop.putHTML("page_list_" + entCount + "_peername", (searchProfile.remotepeer == null) ? "<unknown>" : searchProfile.remotepeer.getName());
                    prop.put("page_list_" + entCount + "_queryhashes", plasmaSearchQuery.anonymizedQueryHashes(searchProfile.queryHashes));
                }
                prop.putNum("page_list_" + entCount + "_querycount", searchProfile.linesPerPage);
                prop.putNum("page_list_" + entCount + "_resultcount", searchProfile.resultcount);
                prop.putNum("page_list_" + entCount + "_urltime", searchProfile.urlretrievaltime);
                prop.putNum("page_list_" + entCount + "_snippettime", searchProfile.snippetcomputationtime);
                prop.putNum("page_list_" + entCount + "_resulttime", searchProfile.searchtime);
                qcountSum += searchProfile.linesPerPage;
                rcountSum += searchProfile.resultcount;
                utimeSum += searchProfile.urlretrievaltime;
                stimeSum += searchProfile.snippetcomputationtime;
                rtimeSum += searchProfile.searchtime;
                if (searchProfile.resultcount > 0){
                	rcount++;
                    utimeSum1 += searchProfile.urlretrievaltime;
                    stimeSum1 += searchProfile.snippetcomputationtime;
                    rtimeSum1 += searchProfile.searchtime;
                }
            }
            prop.put("page_list", m);
            prop.put("page_num", m);
            prop.put("page_resultcount", rcount);
            
            // Put -1 instead of NaN as result for empty search list
            if (m == 0) m = -1;
            if (rcount == 0) rcount = -1;
            prop.putNum("page_querycount_avg", (double) qcountSum / m);
            prop.putNum("page_resultcount_avg", (double) rcountSum / m);
            prop.putNum("page_urltime_avg", (double) utimeSum / m);
            prop.putNum("page_snippettime_avg", (double) stimeSum / m);
            prop.putNum("page_resulttime_avg", (double) rtimeSum / m);
            prop.putNum("page_resultcount_avg1", (double) rcountSum / rcount);
            prop.putNum("page_urltime_avg1", (double) utimeSum1 / rcount);
            prop.putNum("page_snippettime_avg1", (double) stimeSum1 / rcount);
            prop.putNum("page_resulttime_avg1", (double) rtimeSum1 / rcount);
            prop.putNum("page_total", (page == 2) ? sb.localSearches.size() : sb.remoteSearches.size());
        }
        if ((page == 3) || (page == 5)) {
            final Iterator<Entry<String, TreeSet<Long>>> i = (page == 3) ? sb.localSearchTracker.entrySet().iterator() : sb.remoteSearchTracker.entrySet().iterator();
            String host;
            TreeSet<Long> handles;
            int entCount = 0;
            int qphSum = 0;
            Map.Entry<String, TreeSet<Long>> entry;
            try {
            while ((entCount < maxCount) && (i.hasNext())) {
                entry = i.next();
                host = entry.getKey();
                handles = entry.getValue();
                
                int dateCount = 0;
                final Iterator<Long> ii = handles.iterator();
                while (ii.hasNext()) {
                	final Long timestamp = ii.next();
                	prop.put("page_list_" + entCount + "_dates_" + dateCount + "_date", DateFormatter.formatShortSecond(new Date(timestamp.longValue())));
                	prop.put("page_list_" + entCount + "_dates_" + dateCount + "_timestamp", timestamp.toString());
                	dateCount++;
                }
                prop.put("page_list_" + entCount + "_dates", dateCount);
                final int qph = handles.tailSet(Long.valueOf(System.currentTimeMillis() - 1000 * 60 * 60)).size();
                qphSum += qph;
                prop.put("page_list_" + entCount + "_qph", qph);
                
                prop.put("page_list_" + entCount + "_dark", ((dark) ? 1 : 0) ); dark =! dark;
                prop.putHTML("page_list_" + entCount + "_host", host);
                if (page == 5) {
                    final yacySeed remotepeer = sb.webIndex.seedDB.lookupByIP(natLib.getInetAddress(host), true, true, true);
                    prop.putHTML("page_list_" + entCount + "_peername", (remotepeer == null) ? "UNKNOWN" : remotepeer.getName());
                }
                prop.putNum("page_list_" + entCount + "_count", handles.size());

                // next
                entCount++;
            }
            } catch (final ConcurrentModificationException e) {} // we dont want to synchronize this
            prop.put("page_list", entCount);
            prop.putNum("page_num", entCount);
            prop.putNum("page_total", (page == 3) ? sb.localSearches.size() : sb.remoteSearches.size());
            prop.putNum("page_qph_sum", qphSum);
        }
        // return rewrite properties
        return prop;
    }
 
}
