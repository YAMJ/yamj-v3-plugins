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
package org.yamj.plugin.allocine;

import com.moviejukebox.allocine.model.*;
import java.util.*;
import java.util.Map.Entry;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.artwork.*;
import org.yamj.plugin.api.model.*;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class AllocineArtworkScanner extends AbstractAllocineScanner 
    implements MovieArtworkScanner, SeriesArtworkScanner, PersonArtworkScanner
{
    private static final Logger LOG = LoggerFactory.getLogger(AllocineArtworkScanner.class);

    @Override
    public List<ArtworkDTO> getPosters(IMovie movie) {
        String allocineId = getMovieId(movie, false);
        if (isNoValidAllocineId(allocineId)) {
            LOG.debug("Allocine id not available '{}'", movie.getTitle()); //NOSONAR
            return null; //NOSONAR
        }

        MovieInfos movieInfos = allocineApiWrapper.getMovieInfos(allocineId, false);
        if (movieInfos == null || movieInfos.isNotValid() || MapUtils.isEmpty(movieInfos.getPosters())) {
            return null; //NOSONAR
        }
        
        return buildArtworkDetails(movieInfos.getPosters());
    }

    @Override
    public List<ArtworkDTO> getFanarts(IMovie movie) {
        return null; //NOSONAR
    }

    @Override
    public List<ArtworkDTO> getPosters(ISeason season) {
        String allocineId = getSeriesId(season.getSeries(), false);
        if (isNoValidAllocineId(allocineId)) {
            LOG.debug("Allocine id not available '{}' - Season {}", season.getSeries().getTitle(), season.getNumber());
            return null; //NOSONAR
        }

        TvSeasonInfos tvSeasonInfos = allocineApiWrapper.getTvSeasonInfos(allocineId);
        if (tvSeasonInfos == null || tvSeasonInfos.isNotValid() || MapUtils.isEmpty(tvSeasonInfos.getPosters())) {
            return null; //NOSONAR
        }
        
        return buildArtworkDetails(tvSeasonInfos.getPosters());
    }

    @Override
    public List<ArtworkDTO> getPosters(ISeries series) {
        String allocineId = getSeriesId(series, false);
        if (isNoValidAllocineId(allocineId)) {
            LOG.debug("Allocine id not available '{}'", series.getTitle()); //NOSONAR
            return null; //NOSONAR
        }
        
        TvSeriesInfos tvSeriesInfos = allocineApiWrapper.getTvSeriesInfos(allocineId, false);
        if (tvSeriesInfos == null || tvSeriesInfos.isNotValid() || MapUtils.isEmpty(tvSeriesInfos.getPosters())) {
            return null; //NOSONAR
        }
        
        return buildArtworkDetails(tvSeriesInfos.getPosters());
    }

    @Override
    public List<ArtworkDTO> getFanarts(ISeason season) {
        return null; //NOSONAR
    }

    @Override
    public List<ArtworkDTO> getFanarts(ISeries series) {
        return null; //NOSONAR
    }

    @Override
    public List<ArtworkDTO> getBanners(ISeason season) {
        return null; //NOSONAR
    }

    @Override
    public List<ArtworkDTO> getBanners(ISeries series) {
        return null; //NOSONAR
    }

    @Override
    public List<ArtworkDTO> getVideoImages(IEpisode episode) {
        return null; //NOSONAR
    }

    @Override
    public List<ArtworkDTO> getPhotos(IPerson person) {
        String allocineId = getPersonId(person, false);
        if (isNoValidAllocineId(allocineId)) {
            LOG.debug("Allocine id not available '{}'", person.getName()); //NOSONAR
            return null; //NOSONAR
        }
        
        PersonInfos personInfos = allocineApiWrapper.getPersonInfos(allocineId, false);
        if (personInfos == null || personInfos.isNotValid() || StringUtils.isBlank(personInfos.getPhotoURL())) {
            return null; //NOSONAR
        }

        ArtworkDTO dto = new ArtworkDTO(getScannerName(), personInfos.getPhotoURL(), allocineId);
        return Collections.singletonList(dto);
    }


    private static List<ArtworkDTO> buildArtworkDetails(Map<String,Long> artworks) {
        List<ArtworkDTO> dtos = new ArrayList<>(artworks.size());
        for (Entry<String,Long> entry : artworks.entrySet()) {
            final String hashCode;
            if (entry.getValue() == null || entry.getValue().longValue() == 0) {
                hashCode = ArtworkTools.getSimpleHashCode(entry.getKey());
            } else {
                hashCode = entry.getValue().toString();
            }
            ArtworkDTO dto = new ArtworkDTO(AllocinePlugin.SCANNER_NAME, entry.getKey(), hashCode);
            dtos.add(dto);
        }
        return dtos;
    }
}
