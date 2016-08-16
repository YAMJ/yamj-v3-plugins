/*
 *      Copyright (c) 2004-2016 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ) plugins.
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3-plugins
 *
 */
package org.yamj.plugin.imdb;

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;

import com.omertron.imdbapi.model.ImdbCast;
import com.omertron.imdbapi.model.ImdbCredit;
import com.omertron.imdbapi.model.ImdbPerson;
import java.util.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.NeedsConfigService;
import org.yamj.plugin.api.NeedsLocaleService;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.NfoScanner;
import org.yamj.plugin.api.model.*;
import org.yamj.plugin.api.model.type.JobType;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.service.PluginLocaleService;
import org.yamj.plugin.api.web.HTMLTools;
 
public abstract class AbstractImdbScanner implements NfoScanner, NeedsConfigService, NeedsLocaleService {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractImdbScanner.class);
    protected static final String HTML_DIV_END = "</div>";
    protected static final String HTML_A_END = "</a>";
    protected static final String HTML_H4_END = ":</h4>";
    protected static final String HTML_TABLE_END = "</table>";
    protected static final String HTML_TD_END = "</td>";
    protected static final String LITERAL_NORMAL = "normal";
    
    protected PluginConfigService configService;
    protected PluginLocaleService localeService;
    protected ImdbApiWrapper imdbApiWrapper;
    private ImdbSearchEngine imdbSearchEngine;

    @Override
    public final String getScannerName() {
        return SOURCE_IMDB;
    }

    @Override
    public final void setConfigService(PluginConfigService configService) {
        this.configService = configService;
        // also set the API wrapper
        this.imdbApiWrapper = ImdbPlugin.getImdbApiWrapper();
        this.imdbSearchEngine = ImdbPlugin.getImdbSearchEngine();
    }

    @Override
    public final void setLocaleService(PluginLocaleService localeService) {
        this.localeService = localeService;
    }

    @Override
    public boolean scanNFO(String nfoContent, IdMap idMap) {
        // if we already have the ID, skip the scanning of the NFO file
        final boolean ignorePresentId = configService.getBooleanProperty("imdb.nfo.ignore.present.id", false);
        // if we already have the ID, skip the scanning of the NFO file
        if (!ignorePresentId && isValidImdbId(idMap.getId(SOURCE_IMDB))) {
            return true;
        }

        LOG.trace("Scanning NFO for IMDb ID");

        try {
            int beginIndex = nfoContent.indexOf("/tt");
            if (beginIndex != -1) {
                String imdbId = new StringTokenizer(nfoContent.substring(beginIndex + 1), "/ \n,:!&ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©\"'(--ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨_ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â )=$").nextToken();
                LOG.debug("IMDb ID found in NFO: {}", imdbId);
                idMap.addId(SOURCE_IMDB, imdbId);
                return true;
            }

            beginIndex = nfoContent.indexOf("/Title?");
            if (beginIndex != -1 && beginIndex + 7 < nfoContent.length()) {
                String imdbId = "tt" + new StringTokenizer(nfoContent.substring(beginIndex + 7), "/ \n,:!&ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©\"'(--ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨_ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â )=$").nextToken();
                LOG.debug("IMDb ID found in NFO: {}", imdbId);
                idMap.addId(SOURCE_IMDB, imdbId);
                return true;
            }
        } catch (Exception ex) {
            LOG.trace("NFO scanning error", ex);
        }

        LOG.debug("No IMDb ID found in NFO");
        return false;
    }

    public String getMovieId(IMovie movie, boolean throwTempError) {
        String imdbId = movie.getId(SOURCE_IMDB);

        // search by title
        if (isNoValidImdbId(imdbId)) {
            imdbId = imdbSearchEngine.getImdbId(movie.getTitle(), movie.getYear(), false, throwTempError);
            movie.addId(SOURCE_IMDB, imdbId);
        }
        
        // search by original title
        if (isNoValidImdbId(imdbId) && MetadataTools.isOriginalTitleScannable(movie)) {
            imdbId = imdbSearchEngine.getImdbId(movie.getOriginalTitle(), movie.getYear(), false, throwTempError);
            movie.addId(SOURCE_IMDB, imdbId);
        }
        
        return imdbId;
    }

    public String getSeriesId(ISeries series, boolean throwTempError) {
        String imdbId = series.getId(SOURCE_IMDB);
        if (isNoValidImdbId(imdbId)) {
            imdbId = imdbSearchEngine.getImdbId(series.getTitle(), series.getStartYear(), true, throwTempError);
            series.addId(SOURCE_IMDB, imdbId);
        }
        return imdbId;
    }

    public String getPersonId(IPerson person, boolean throwTempError) {
        String imdbId = person.getId(SOURCE_IMDB);
        if (isValidImdbId(imdbId)) {
            return imdbId;
        }
        if (StringUtils.isNotBlank(person.getName())) {
            imdbId = this.imdbSearchEngine.getImdbPersonId(person.getName(), throwTempError);
            person.addId(SOURCE_IMDB, imdbId);
        }
        return imdbId;
    }

    protected static boolean isValidImdbId(String imdbId) {
        return StringUtils.isNotBlank(imdbId);
    }

    protected static boolean isNoValidImdbId(String imdbId) {
        return StringUtils.isBlank(imdbId);
    }
    
    protected static String parseOriginalTitle(String xml) {
        return HTMLTools.extractTag(xml, "<span class=\"title-extra\">", "</span>")
            .replaceFirst("<i>(original title)</i>", StringUtils.EMPTY)
            .replace("\"", StringUtils.EMPTY)
            .trim();
     }

    protected void parseCastCrew(ICredits credits, String imdbId) {
        List<ImdbCredit> fullCast = imdbApiWrapper.getFullCast(imdbId);
        
        if (CollectionUtils.isEmpty(fullCast)) {
            LOG.info("No cast for imdb ID: {}", imdbId);
            return;
        }

        // build jobs map
        EnumMap<JobType,List<ImdbCast>> jobs = getJobs(fullCast);
        // get configuration parameters
        boolean skipFaceless = configService.getBooleanProperty("imdb.castcrew.skip.faceless", false);
        boolean skipUncredited = configService.getBooleanProperty("imdb.castcrew.skip.uncredited", true);
        
        // add credits
        addCredits(credits, JobType.DIRECTOR, jobs, skipUncredited, skipFaceless);
        addCredits(credits, JobType.WRITER, jobs, skipUncredited, skipFaceless);
        addCredits(credits, JobType.ACTOR, jobs, skipUncredited, skipFaceless);
        addCredits(credits, JobType.PRODUCER, jobs, skipUncredited, skipFaceless);
        addCredits(credits, JobType.CAMERA, jobs, skipUncredited, skipFaceless);
        addCredits(credits, JobType.EDITING, jobs, skipUncredited, skipFaceless);
        addCredits(credits, JobType.ART, jobs, skipUncredited, skipFaceless);
        addCredits(credits, JobType.SOUND, jobs, skipUncredited, skipFaceless);
        addCredits(credits, JobType.EFFECTS, jobs, skipUncredited, skipFaceless);
        addCredits(credits, JobType.LIGHTING, jobs, skipUncredited, skipFaceless);
        addCredits(credits, JobType.COSTUME_MAKEUP, jobs, skipUncredited, skipFaceless);
        addCredits(credits, JobType.CREW, jobs, skipUncredited, skipFaceless);
        addCredits(credits, JobType.UNKNOWN, jobs, skipUncredited, skipFaceless);
    }

    private static EnumMap<JobType,List<ImdbCast>>  getJobs(List<ImdbCredit> credits) {
        EnumMap<JobType,List<ImdbCast>> result = new EnumMap<>(JobType.class);
        
        for (ImdbCredit credit : credits) {
            if (CollectionUtils.isEmpty(credit.getCredits())) {
                continue;
            }
            
            switch (credit.getToken()) {
                case "cast":
                    result.put(JobType.ACTOR, credit.getCredits());
                    break;
                case "writers":
                    result.put(JobType.WRITER, credit.getCredits());
                    break;
                case "directors":
                    result.put(JobType.DIRECTOR, credit.getCredits());
                    break;
                case "cinematographers":
                    result.put(JobType.CAMERA, credit.getCredits());
                    break;
                case "editors":
                    result.put(JobType.EDITING, credit.getCredits());
                    break;
                case "producers":
                case "casting_directors":
                    if (result.containsKey(JobType.PRODUCER)) {
                        result.get(JobType.PRODUCER).addAll(credit.getCredits());
                    } else {
                        result.put(JobType.PRODUCER, credit.getCredits());
                    }
                    break;
                case "music_original":
                    result.put(JobType.SOUND, credit.getCredits());
                    break;
                case "production_designers":
                case "art_directors":
                case "set_decorators":
                    if (result.containsKey(JobType.ART)) {
                        result.get(JobType.ART).addAll(credit.getCredits());
                    } else {
                        result.put(JobType.ART, credit.getCredits());
                    }
                    break;
                case "costume_designers":
                    if (result.containsKey(JobType.COSTUME_MAKEUP)) {
                        result.get(JobType.COSTUME_MAKEUP).addAll(credit.getCredits());
                    } else {
                        result.put(JobType.COSTUME_MAKEUP, credit.getCredits());
                    }
                    break;
                case "assistant_directors":
                case "production_managers":
                case "art_department":
                case "sound_department":
                case "special_effects_department":
                case "visual_effects_department":
                case "stunts":
                case "camera_department":
                case "animation_department":
                case "casting_department":
                case "costume_department":
                case "editorial_department":
                case "music_department":
                case "transportation_department":
                case "make_up_department":
                case "miscellaneous":
                    if (result.containsKey(JobType.CREW)) {
                        result.get(JobType.CREW).addAll(credit.getCredits());
                    } else {
                        result.put(JobType.CREW, credit.getCredits());
                    }
                    break;
                default:
                    if (result.containsKey(JobType.UNKNOWN)) {
                        result.get(JobType.UNKNOWN).addAll(credit.getCredits());
                    } else {
                        result.put(JobType.UNKNOWN, credit.getCredits());
                    }
                    break;
            }
        }
        
        return result;
    }
    
    private void addCredits(ICredits credits, JobType jobType, EnumMap<JobType,List<ImdbCast>> jobs, boolean skipUncredited, boolean skipFaceless) {
        if (CollectionUtils.isEmpty(jobs.get(jobType))) {
            return;
        }
        if (!this.configService.isCastScanEnabled(jobType)) {
            return;
        }
            
        for (ImdbCast cast : jobs.get(jobType)) {
            final ImdbPerson person = cast.getPerson();
            if (person == null || StringUtils.isBlank(person.getName())) {
                continue; //NOSONAR
            }
            
            if (skipUncredited && StringUtils.contains(cast.getAttr(), "(uncredited")) {
                continue; //NOSONAR
            }

            final String photoURL = (person.getImage() == null) ? null : person.getImage().getUrl();
            if (skipFaceless && JobType.ACTOR.equals(jobType) && StringUtils.isEmpty(photoURL)) {
                // skip faceless actors only
                continue; //NOSONAR
            }

            credits.addCredit(person.getActorId(), jobType,  person.getName(), cast.getCharacter());
        }
    }

    protected void parseReleasedTitles(ICombined combined, String imdbId, Locale locale) {
        // get the AKS
        Map<String, String> akas = getAkaMap(imdbId);
        if (MapUtils.isEmpty(akas)) {
            return;
        }
        
        // ORIGINAL TITLE

        // get the AKAs from release info XML
        for (Map.Entry<String, String> aka : akas.entrySet()) {
            if (StringUtils.indexOfIgnoreCase(aka.getKey(), "original title") > 0) {
                combined.setOriginalTitle(aka.getValue().trim());
                break;
            }
        }

        // TITLE for preferred country from AKAS
        if (!configService.getBooleanProperty("imdb.aka.scrape.title", false)) {
            return;
        }
        
        List<String> akaIgnoreVersions = configService.getPropertyAsList("imdb.aka.ignore.versions", "");

        // build countries to search for within AKA list
        Set<String> akaMatchingCountries = new TreeSet<>(localeService.getCountryNames(locale.getCountry()));
        for (String fallback : configService.getPropertyAsList("imdb.aka.fallback.countries", "")) {
            String countryCode = localeService.findCountryCode(fallback);
            akaMatchingCountries.addAll(localeService.getCountryNames(countryCode));
        }

        String foundValue = null;
        // NOTE: First matching country is the preferred country
        outerLoop: for (String matchCountry : akaMatchingCountries) {
            innerLoop: for (Map.Entry<String, String> aka : akas.entrySet()) {
                int startIndex = aka.getKey().indexOf(matchCountry);
                if (startIndex < 0) {
                    continue innerLoop;
                }

                String extracted = aka.getKey().substring(startIndex);
                int endIndex = extracted.indexOf('/');
                if (endIndex > -1) {
                    extracted = extracted.substring(0, endIndex);
                }

                if (isNotIgnored(extracted, akaIgnoreVersions)) {
                    foundValue = aka.getValue().trim();
                    break outerLoop;
                }
            }
        }
        combined.setTitle(foundValue);
    }
    
    private static final boolean isNotIgnored(String value, List<String> ignoreVersions) {
        for (String ignore : ignoreVersions) {
            if (StringUtils.isNotBlank(ignore) && StringUtils.containsIgnoreCase(value, ignore.trim())) {
                return false;
            }
        }
        return true;
    }
    
    private Map<String, String> getAkaMap(String imdbId) {
        String releaseInfoXML = imdbApiWrapper.getReleasInfoXML(imdbId);
        if (releaseInfoXML != null) {
            // Just extract the AKA section from the page
            List<String> akaList = HTMLTools.extractTags(releaseInfoXML, "<a id=\"akas\" name=\"akas\">", HTML_TABLE_END, "<td>", HTML_TD_END, false);
            return buildAkaMap(akaList);
        }
        return null;
    }

    /**
     * Create a map of the AKA values
     *
     * @param list
     * @return
     */
    private static Map<String, String> buildAkaMap(List<String> list) {
        Map<String, String> map = new LinkedHashMap<>();
        int i = 0;
        do {
            try {
                String key = list.get(i++);
                String value = list.get(i++);
                map.put(key, value);
            } catch (Exception ignore) { //NOSONAR
                i = -1;
            }
        } while (i != -1);
        return map;
    }
}

