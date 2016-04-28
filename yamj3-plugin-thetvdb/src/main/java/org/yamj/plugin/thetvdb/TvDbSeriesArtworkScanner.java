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
package org.yamj.plugin.thetvdb;

import static org.yamj.plugin.api.Constants.SOURCE_TVDB;

import com.omertron.thetvdbapi.model.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.artwork.ArtworkTools;
import org.yamj.plugin.api.artwork.SeriesArtworkScanner;
import org.yamj.plugin.api.model.IEpisode;
import org.yamj.plugin.api.model.ISeason;
import org.yamj.plugin.api.model.ISeries;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class TvDbSeriesArtworkScanner extends AbstractTheTvDbScanner implements SeriesArtworkScanner {

    private static final Logger LOG = LoggerFactory.getLogger(TvDbSeriesArtworkScanner.class);

    @Override
    public List<ArtworkDTO> getPosters(ISeason season) {
        String id = getSeriesId(season.getSeries(), false);
        if (isNoValidTheTvDbId(id)) {
            return null;
        }

        LOG.debug("Scan posters for season {}-{}", id, season.getNumber());

        List<ArtworkDTO> langDTOs = new ArrayList<>(5);
        List<ArtworkDTO> altLangDTOs = new ArrayList<>(5);
        List<ArtworkDTO> noLangDTOs = new ArrayList<>(5);

        final String language = localeService.getLocale().getLanguage();
        final String altLanguage = configService.getProperty("thetvdb.language.alternate", language);
        
        // get series artwork
        final Banners bannerList = theTvDbApiWrapper.getBanners(id);
        if (bannerList != null) {
            // find posters
            for (Banner banner : bannerList.getSeasonList()) {
                if ((banner.getSeason() == season.getNumber()) && (banner.getBannerType2() == BannerType.SEASON)) {
                    if (StringUtils.isBlank(banner.getLanguage())) {
                        noLangDTOs.add(createArtworDetail(banner));
                    } else if (banner.getLanguage().equalsIgnoreCase(language)) {
                        langDTOs.add(createArtworDetail(banner));
                    } else if (banner.getLanguage().equalsIgnoreCase(altLanguage)) {
                        altLangDTOs.add(createArtworDetail(banner));
                    }
                }
            }
        }
        
        LOG.debug("Season {}-{}: Found {} posters for language '{}'", id, season.getNumber(), langDTOs.size(), altLanguage);
        if (!language.equalsIgnoreCase(altLanguage)) {
            LOG.debug("Season {}-{}: Found {} posters for alternate language '{}'", id, season.getNumber(), altLangDTOs.size(), altLanguage);
        }
        LOG.debug("Season {}-{}: Found {} posters without language", id, season.getNumber(), noLangDTOs.size());

        final List<ArtworkDTO> returnDTOs;
        if (!langDTOs.isEmpty()) {
            LOG.info("Season {}-{}: Using posters with language '{}'", id, season.getNumber(), language);
            returnDTOs = langDTOs;
        } else if (!altLangDTOs.isEmpty()) {
            LOG.info("Season {}-{}: No poster found for language '{}', using posters with language '{}'", id, season.getNumber(), language, altLanguage);
            returnDTOs = altLangDTOs;
        } else if (!noLangDTOs.isEmpty()) {
            LOG.info("Season {}-{}: No poster found for language '{}', using posters with no language", id, season.getNumber(), language);
            returnDTOs = noLangDTOs;
        } else if (bannerList != null && CollectionUtils.isNotEmpty(bannerList.getPosterList())) {
            LOG.info("Season {}-{}: No poster found by language, using first series poster found", id, season.getNumber());
            returnDTOs = new ArrayList<>(1);
            Banner banner = bannerList.getPosterList().get(0);
            returnDTOs.add(createArtworDetail(banner));
        } else {
            Series tvdbSeries = theTvDbApiWrapper.getSeries(id, language);
            if (tvdbSeries == null || StringUtils.isBlank(tvdbSeries.getPoster())) {
                returnDTOs = null;
            } else {
                LOG.info("Season {}-{}: Using default series poster", id, season.getNumber());
                ArtworkDTO artworkDTO = new ArtworkDTO(SOURCE_TVDB, tvdbSeries.getPoster(), ArtworkTools.getPartialHashCode(tvdbSeries.getPoster()));
                returnDTOs = Collections.singletonList(artworkDTO);
            }
        }

        return returnDTOs;
    }

    @Override
    public List<ArtworkDTO> getPosters(ISeries series) {
        String id = getSeriesId(series, false);
        if (isNoValidTheTvDbId(id)) {
            return null;
        }
  
        LOG.debug("Scan posters for series {}", id);
      
        List<ArtworkDTO> langDTOs = new ArrayList<>(5);
        List<ArtworkDTO> altLangDTOs = new ArrayList<>(5);
        List<ArtworkDTO> noLangDTOs = new ArrayList<>(5);

        final String language = localeService.getLocale().getLanguage();
        final String altLanguage = configService.getProperty("thetvdb.language.alternate", language);

        // get series artwork
        final Banners bannerList = theTvDbApiWrapper.getBanners(id);
        if (bannerList != null) {
            // find posters
            for (Banner banner : bannerList.getPosterList()) {
                if (banner.getBannerType2() == BannerType.POSTER) {
                    if (StringUtils.isBlank(banner.getLanguage())) {
                        noLangDTOs.add(createArtworDetail(banner));
                    } else if (banner.getLanguage().equalsIgnoreCase(language)) {
                        langDTOs.add(createArtworDetail(banner));
                    } else if (banner.getLanguage().equalsIgnoreCase(altLanguage)) {
                        altLangDTOs.add(createArtworDetail(banner));
                    }
                }
            }
        }
        
        LOG.debug("Series {}: Found {} posters for language '{}'", id, langDTOs.size(), language);
        if (!language.equalsIgnoreCase(altLanguage)) {
            LOG.debug("Series {}: Found {} posters for alternate language '{}'", id, altLangDTOs.size(), altLanguage);
        }
        LOG.debug("Series {}: Found {} posters without language", id, noLangDTOs.size());

        final List<ArtworkDTO> returnDTOs;
        if (!langDTOs.isEmpty()) {
            LOG.info("Series {}: Using posters with language '{}'", id, language);
            returnDTOs = langDTOs;
        } else if (!altLangDTOs.isEmpty()) {
            LOG.info("Series {}: No poster found for language '{}', using posters with language '{}'", id, language, altLanguage);
            returnDTOs = altLangDTOs;
        } else if (!noLangDTOs.isEmpty()) {
            LOG.info("Series {}: No poster found for language '{}', using posters with no language", id, language);
            returnDTOs = noLangDTOs;
        } else {
            Series tvdbSeries = theTvDbApiWrapper.getSeries(id, language);
            if (tvdbSeries == null || StringUtils.isBlank(tvdbSeries.getPoster())) {
                returnDTOs = null;
            } else {
                LOG.info("Series {}: Using default series poster", id);
                ArtworkDTO artworkDTO = new ArtworkDTO(SOURCE_TVDB, tvdbSeries.getPoster(), ArtworkTools.getPartialHashCode(tvdbSeries.getPoster()));
                returnDTOs = Collections.singletonList(artworkDTO);
            }
        }

        return returnDTOs;
    }

    /**
     * NOTE: No explicit season fanart; so right now the same as series fanarts
     */
    @Override
    public List<ArtworkDTO> getFanarts(ISeason season) {
        String id = getSeriesId(season.getSeries(), false);
        if (isNoValidTheTvDbId(id)) {
            return null;
        }
  
        LOG.debug("Scan fanarts for season {}-{}", id, season.getNumber());
      
        List<ArtworkDTO> hdDTOs = new ArrayList<>(5);
        List<ArtworkDTO> sdDTOs = new ArrayList<>(5);

        // get series artwork
        final Banners bannerList = theTvDbApiWrapper.getBanners(id);
        if (bannerList != null) {
            // find fanart
            for (Banner banner : bannerList.getFanartList()) {
                if (banner.getBannerType2() == BannerType.FANART_HD) {
                    // HD fanart
                    hdDTOs.add(createArtworDetail(banner));
                } else {
                    // SD fanart
                    sdDTOs.add(createArtworDetail(banner));
                }
            }
        }
        
        LOG.debug("Season {}-{}: Found {} HD fanart", id, season.getNumber(), hdDTOs.size());
        LOG.debug("Season {}-{}: Found {} SD fanart", id, season.getNumber(), sdDTOs.size());

        final List<ArtworkDTO> returnDTOs;
        if (!hdDTOs.isEmpty()) {
            LOG.debug("Season {}-{}: Using HD fanart", id, season.getNumber());
            returnDTOs = hdDTOs;
        } else if (!sdDTOs.isEmpty()) {
            LOG.debug("Season {}-{}: No HD fanart found; using SD fanart", id, season.getNumber());
            returnDTOs = sdDTOs;
        } else {
            final String language = localeService.getLocale().getLanguage();
            Series tvdbSeries = theTvDbApiWrapper.getSeries(id, language);
            if (tvdbSeries == null || StringUtils.isBlank(tvdbSeries.getFanart())) {
                returnDTOs = null;
            } else {
                LOG.debug("Season {}-{}: Using default series fanart", id, season.getNumber());
                ArtworkDTO artworkDTO = new ArtworkDTO(SOURCE_TVDB, tvdbSeries.getFanart(), ArtworkTools.getPartialHashCode(tvdbSeries.getFanart()));
                returnDTOs = Collections.singletonList(artworkDTO);
            }
        }

        return returnDTOs;
    }

    @Override
    public List<ArtworkDTO> getFanarts(ISeries series) {
        String id = getSeriesId(series, false);
        if (isNoValidTheTvDbId(id)) {
            return null;
        }
  
        LOG.debug("Scan fanarts for series {}", id);

        List<ArtworkDTO> hdDTOs = new ArrayList<>(5);
        List<ArtworkDTO> sdDTOs = new ArrayList<>(5);

        // get series artwork
        final Banners bannerList = theTvDbApiWrapper.getBanners(id);
        if (bannerList != null) {
            // find fanart
            for (Banner banner : bannerList.getFanartList()) {
                if (banner.getBannerType2() == BannerType.FANART_HD) {
                    // HD fanart
                    hdDTOs.add(createArtworDetail(banner));
                } else {
                    // SD fanart
                    sdDTOs.add(createArtworDetail(banner));
                }
            }
        }
        
        LOG.debug("Series {}: Found {} HD fanart", id, hdDTOs.size());
        LOG.debug("Series {}: Found {} SD fanart", id, sdDTOs.size());

        final List<ArtworkDTO> returnDTOs;
        if (!hdDTOs.isEmpty()) {
            LOG.info("Series {}: Using HD fanart", id);
            returnDTOs = hdDTOs;
        } else if (!sdDTOs.isEmpty()) {
            LOG.info("Series {}: No HD fanart found; using SD fanart", id);
            returnDTOs = sdDTOs;
        } else {
            final String language = localeService.getLocale().getLanguage();
            Series tvdbSeries = theTvDbApiWrapper.getSeries(id, language);
            if (tvdbSeries == null || StringUtils.isBlank(tvdbSeries.getFanart())) {
                returnDTOs = null;
            } else {
                LOG.info("Series {}: Using default series fanart", id);
                ArtworkDTO artworkDTO = new ArtworkDTO(SOURCE_TVDB, tvdbSeries.getFanart(), ArtworkTools.getPartialHashCode(tvdbSeries.getFanart()));
                returnDTOs = Collections.singletonList(artworkDTO);
            }
        }

        return returnDTOs;
    }

    @Override
    public List<ArtworkDTO> getBanners(ISeason season) {
        String id = getSeriesId(season.getSeries(), false);
        if (isNoValidTheTvDbId(id)) {
            return null;
        }
  
        LOG.debug("Scan banners for season {}-{}", id, season.getNumber());
      
        List<ArtworkDTO> seasonLangDTOs = new ArrayList<>(5);
        List<ArtworkDTO> seasonAltLangDTOs = new ArrayList<>(5);
        List<ArtworkDTO> seasonNoLangDTOs = new ArrayList<>(5);
        List<ArtworkDTO> seriesLangDTOs = new ArrayList<>(5);
        List<ArtworkDTO> seriesAltLangDTOs = new ArrayList<>(5);
        List<ArtworkDTO> seriesNoLangDTOs = new ArrayList<>(5);
        List<ArtworkDTO> blankDTOs = new ArrayList<>(5);

        final String language = localeService.getLocale().getLanguage();
        final String altLanguage = configService.getProperty("thetvdb.language.alternate", language);
        final boolean seasonBannerOnlySeries = configService.getBooleanProperty("thetvdb.season.banner.onlySeries", false);

        // get series artwork
        final Banners bannerList = theTvDbApiWrapper.getBanners(id);
        if (bannerList != null) {
            // series banners
            if (!seasonBannerOnlySeries) {
                // season banners
                for (Banner banner : bannerList.getSeasonList()) {
                    if (banner.getBannerType2() == BannerType.SEASONWIDE) {
                        if (StringUtils.isBlank(banner.getLanguage())) {
                            seasonNoLangDTOs.add(createArtworDetail(banner));
                        } else if (banner.getLanguage().equalsIgnoreCase(language)) {
                            seasonLangDTOs.add(createArtworDetail(banner));
                        } else if (banner.getLanguage().equalsIgnoreCase(altLanguage)) {
                            seasonAltLangDTOs.add(createArtworDetail(banner));
                        }
                    }
                }
            }
            for (Banner banner : bannerList.getSeasonList()) {
                if (banner.getBannerType2() == BannerType.GRAPHICAL) {
                    if (StringUtils.isBlank(banner.getLanguage())) {
                        seriesNoLangDTOs.add(createArtworDetail(banner));
                    } else if (banner.getLanguage().equalsIgnoreCase(language)) {
                        seriesLangDTOs.add(createArtworDetail(banner));
                    } else if (banner.getLanguage().equalsIgnoreCase(altLanguage)) {
                        seriesAltLangDTOs.add(createArtworDetail(banner));
                    }
                } else if (banner.getBannerType2() == BannerType.BLANK) {
                    blankDTOs.add(createArtworDetail(banner));
                }
            }
        }
        
        if (!seasonBannerOnlySeries) {
            LOG.debug("Season {}-{}: Found {} season banners for language '{}'", id, season.getNumber(), seasonLangDTOs.size(), language);
            if (!language.equalsIgnoreCase(altLanguage)) {
                LOG.debug("Season {}-{}: Found {} season banners for alternate language '{}'", id, season.getNumber(), seasonAltLangDTOs.size(), altLanguage);
            }
            LOG.debug("Season {}-{}: Found {} season banners without language", id, season.getNumber(), seasonNoLangDTOs.size());
        }
        LOG.debug("Season {}-{}: Found {} series banners for language '{}'", id, season.getNumber(), seasonLangDTOs.size(), language);
        if (!language.equalsIgnoreCase(altLanguage)) {
            LOG.debug("Season {}-{}: Found {} series banners for alternate language '{}'", id, season.getNumber(), seasonAltLangDTOs.size(), altLanguage);
        }
        LOG.debug("Season {}-{}: Found {} series banners without language", id, season.getNumber(), seasonNoLangDTOs.size());
        LOG.debug("season {}-{}: Found {} blank banners", id, season.getNumber(), blankDTOs.size());

        final List<ArtworkDTO> returnDTOs;
        if (configService.getBooleanProperty("thetvdb.season.banner.onlySeries", false) && !blankDTOs.isEmpty()) {
            LOG.info("Season {}-{}: Using blanks banners", id, season.getNumber());
            returnDTOs = blankDTOs;
        } else if (!seasonLangDTOs.isEmpty()) {
            LOG.info("Season {}-{}: Using season banners with language '{}'", id, season.getNumber(), language);
            returnDTOs = seasonLangDTOs;
        } else if (!seasonAltLangDTOs.isEmpty()) {
            LOG.info("Season {}-{}: No season banner found for language '{}', using season banners with language '{}'", id, season.getNumber(), language, altLanguage);
            returnDTOs = seasonAltLangDTOs;
        } else if (!seasonNoLangDTOs.isEmpty()) {
            LOG.info("Season {}-{}: No season banner found for language '{}', using season banners with no language", id, season.getNumber(), language);
            returnDTOs = seasonNoLangDTOs;
        } else if (!seriesLangDTOs.isEmpty()) {
            LOG.info("Season {}-{}: Using series banners with language '{}'", id, season.getNumber(), language);
            returnDTOs = seriesLangDTOs;
        } else if (!seriesAltLangDTOs.isEmpty()) {
            LOG.info("Season {}-{}: No series banner found for language '{}', using series banners with language '{}'", id, season.getNumber(), language, altLanguage);
            returnDTOs = seriesAltLangDTOs;
        } else if (!seriesNoLangDTOs.isEmpty()) {
            LOG.info("Season {}-{}: No series banner found for language '{}', using series banners with no language", id, season.getNumber(), language);
            returnDTOs = seriesNoLangDTOs;
        } else if (!blankDTOs.isEmpty()) {
            LOG.info("Season {}-{}: No banner found for language '{}', using blank banners", id, season.getNumber(), language);
            returnDTOs = blankDTOs;
        } else {
            Series tvdbSeries = theTvDbApiWrapper.getSeries(id, language);
            if (tvdbSeries == null || StringUtils.isBlank(tvdbSeries.getBanner())) {
                returnDTOs = null;
            } else {
                LOG.info("Season {}-{}: Using default series banner", id, season.getNumber());
                ArtworkDTO artworkDTO = new ArtworkDTO(SOURCE_TVDB, tvdbSeries.getBanner(), ArtworkTools.getPartialHashCode(tvdbSeries.getBanner()));
                returnDTOs = Collections.singletonList(artworkDTO);
            }
        }

        return returnDTOs;
    }

    @Override
    public List<ArtworkDTO> getBanners(ISeries series) {
        String id = getSeriesId(series, false);
        if (isNoValidTheTvDbId(id)) {
            return null;
        }
  
        LOG.debug("Scan banners for series {}", id);
      
        List<ArtworkDTO> langDTOs = new ArrayList<>(5);
        List<ArtworkDTO> altLangDTOs = new ArrayList<>(5);
        List<ArtworkDTO> noLangDTOs = new ArrayList<>(5);
        List<ArtworkDTO> blankDTOs = new ArrayList<>(5);

        // get series artwork
        final String language = localeService.getLocale().getLanguage();
        final String altLanguage = configService.getProperty("thetvdb.language.alternate", language);

        final Banners bannerList = theTvDbApiWrapper.getBanners(id);
        if (bannerList != null) {
            // find banners
            for (Banner banner : bannerList.getSeriesList()) {
                if (banner.getBannerType2() == BannerType.GRAPHICAL) {
                    if (StringUtils.isBlank(banner.getLanguage())) {
                        noLangDTOs.add(createArtworDetail(banner));
                    } else if (banner.getLanguage().equalsIgnoreCase(language)) {
                        langDTOs.add(createArtworDetail(banner));
                    } else if (banner.getLanguage().equalsIgnoreCase(altLanguage)) {
                        altLangDTOs.add(createArtworDetail(banner));
                    }
                } else if (banner.getBannerType2() == BannerType.BLANK) {
                    blankDTOs.add(createArtworDetail(banner));
                }
            }
        }
        
        LOG.debug("Series {}: Found {} banners for language '{}'", id, langDTOs.size(), language);
        if (!language.equalsIgnoreCase(altLanguage)) {
            LOG.debug("Series {}: Found {} banners for alternate language '{}'", id, altLangDTOs.size(), altLanguage);
        }
        LOG.debug("Series {}: Found {} banners without language", id, noLangDTOs.size());
        LOG.debug("Series {}: Found {} blank banners", id, blankDTOs.size());

        final List<ArtworkDTO> returnDTOs;
        if (!langDTOs.isEmpty()) {
            LOG.info("Series {}: Using banners with language '{}'", id, language);
            returnDTOs = langDTOs;
        } else if (!altLangDTOs.isEmpty()) {
            LOG.info("Series {}: No banner found for language '{}', using banners with language '{}'", id, language, altLanguage);
            returnDTOs = altLangDTOs;
        } else if (!noLangDTOs.isEmpty()) {
            LOG.info("Series {}: No banner found for language '{}', using banners with no language", id, language);
            returnDTOs = noLangDTOs;
        } else if (!blankDTOs.isEmpty()) {
            LOG.info("Series {}: No banner found for language '{}', using blank banners", id, language);
            returnDTOs = blankDTOs;
        } else {
            Series tvdbSeries = theTvDbApiWrapper.getSeries(id, language);
            if (tvdbSeries == null || StringUtils.isBlank(tvdbSeries.getBanner())) {
                returnDTOs = null;
            } else {
                LOG.info("Series {}: Using default series banner", id);
                ArtworkDTO artworkDTO = new ArtworkDTO(SOURCE_TVDB, tvdbSeries.getBanner(), ArtworkTools.getPartialHashCode(tvdbSeries.getBanner()));
                returnDTOs = Collections.singletonList(artworkDTO);
            }
        }

        return returnDTOs;
    }

    private static ArtworkDTO createArtworDetail(Banner banner) {
        String url = banner.getUrl();
        ArtworkDTO dto = new ArtworkDTO(SOURCE_TVDB, url, ArtworkTools.getPartialHashCode(url));

        // set language
        if (StringUtils.isNotBlank(banner.getLanguage())) {
            dto.setLanguageCode(banner.getLanguage());
        }

        // set rating
        if (banner.getRating() != null) {
            try {
                dto.setRating((int) (banner.getRating() * 10));
            } catch (Exception ignore) { //NOSONAR
                // ignore a possible number violation
            }
        }

        return dto;
    }

    @Override
    public List<ArtworkDTO> getVideoImages(IEpisode episode) {
        String id = getSeriesId(episode.getSeason().getSeries(), false);
        if (isNoValidTheTvDbId(id)) {
            return null;
        }
        
        final String language = localeService.getLocale().getLanguage();
        Episode tvdbEpisode = theTvDbApiWrapper.getEpisode(id, episode.getSeason().getNumber(), episode.getNumber(), language);
        if (tvdbEpisode != null && StringUtils.isNotBlank(tvdbEpisode.getFilename())) {
            ArtworkDTO artworkDTO = new ArtworkDTO(SOURCE_TVDB, tvdbEpisode.getFilename(), ArtworkTools.getPartialHashCode(tvdbEpisode.getFilename()));
            return Collections.singletonList(artworkDTO);
        }
        
        return null;
    }
}