DELETE FROM works_charts WHERE chart = 'webtoon_popular';
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:372386', 1, 7786503, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6202', 'mk:mb:372386', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6202', 'url', 'https://www.webtoons.com/en/fantasy/osora/list?title_no=6202'))
         WHERE miko_work_id = 'mk:mb:372386' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260218_141/1771353364216xY7xm_JPEG/0Thumb_Poster_6202.jpg'
             WHERE miko_work_id = 'mk:mb:372386' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:575400', 2, 1501698, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7777', 'mk:mb:575400', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7777', 'url', 'https://www.webtoons.com/en/supernatural/superfish/list?title_no=7777'))
         WHERE miko_work_id = 'mk:mb:575400' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251122_289/1763749819666zsNNI_JPEG/Thumb_Poster_7777.jpg'
             WHERE miko_work_id = 'mk:mb:575400' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:495', 3, 3492980, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8135', 'mk:mb:495', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8135', 'url', 'https://www.webtoons.com/en/romance/tears-on-a-withered-flower/list?title_no=8135'))
         WHERE miko_work_id = 'mk:mb:495' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:495' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250625_81/1750809167593uDiAh_JPEG/Thumb_Poster_8135.jpg'
             WHERE miko_work_id = 'mk:mb:495' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:8544', 4, 15316801, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5879', 'mk:mb:8544', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5879', 'url', 'https://www.webtoons.com/en/romance/the-mafia-nanny/list?title_no=5879'))
         WHERE miko_work_id = 'mk:mb:8544' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260512_219/1778525566678kYNzg_JPEG/2Thumb_Poster_5879.jpg'
             WHERE miko_work_id = 'mk:mb:8544' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:41062', 5, 28762362, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1817', 'mk:mb:41062', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1817', 'url', 'https://www.webtoons.com/en/romance/down-to-earth/list?title_no=1817'))
         WHERE miko_work_id = 'mk:mb:41062' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260721_254/1784563542323KDWGF_JPEG/6Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:41062' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:546240', 6, 3416553, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6510', 'mk:mb:546240', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6510', 'url', 'https://www.webtoons.com/en/romance/star-catcher/list?title_no=6510'))
         WHERE miko_work_id = 'mk:mb:546240' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251122_14/1763761469647NYrfW_JPEG/Thumb_Poster_6510.jpg'
             WHERE miko_work_id = 'mk:mb:546240' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:372789', 7, 3911437, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6078', 'mk:mb:372789', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6078', 'url', 'https://www.webtoons.com/en/fantasy/a-spell-for-a-smith/list?title_no=6078'))
         WHERE miko_work_id = 'mk:mb:372789' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251212_137/17654954973124NnEM_JPEG/Thumb_Poster_6078.jpg'
             WHERE miko_work_id = 'mk:mb:372789' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:575873', 8, 608461, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9842', 'mk:mb:575873', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9842', 'url', 'https://www.webtoons.com/en/drama/daytime-in-the-bunker/list?title_no=9842'))
         WHERE miko_work_id = 'mk:mb:575873' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260404_91/1775232574906JIo1A_JPEG/1Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:575873' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:512292', 9, 1110161, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7583', 'mk:mb:512292', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7583', 'url', 'https://www.webtoons.com/en/romance/the-egg-project/list?title_no=7583'))
         WHERE miko_work_id = 'mk:mb:512292' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250624_169/17506989386080I84L_JPEG/Thumb_Poster_7583.jpg'
             WHERE miko_work_id = 'mk:mb:512292' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:362329', 10, 273878, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10009', 'mk:mb:362329', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10009', 'url', 'https://www.webtoons.com/en/romance/situationship/list?title_no=10009'))
         WHERE miko_work_id = 'mk:mb:362329' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:362329' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260520_127/1779234634070JyV8f_JPEG/7Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:362329' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:547234', 11, 960192, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9321', 'mk:mb:547234', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9321', 'url', 'https://www.webtoons.com/en/fantasy/the-patron-of-villains/list?title_no=9321'))
         WHERE miko_work_id = 'mk:mb:547234' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260116_54/1768529545749srvpX_JPEG/4) Thumb_Poster_9321.jpg'
             WHERE miko_work_id = 'mk:mb:547234' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:57', 12, 39270299, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1571', 'mk:mb:57', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1571', 'url', 'https://www.webtoons.com/en/action/eleceed/list?title_no=1571'))
         WHERE miko_work_id = 'mk:mb:57' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:57' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_253/1738646007830Yuew4_JPEG/1571.jpg'
             WHERE miko_work_id = 'mk:mb:57' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:588', 13, 3449656, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5988', 'mk:mb:588', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5988', 'url', 'https://www.webtoons.com/en/action/the-stellar-swordmaster/list?title_no=5988'))
         WHERE miko_work_id = 'mk:mb:588' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260128_63/1769548892842Rm49b_JPEG/14__Thumb_Poster_5988.jpg'
             WHERE miko_work_id = 'mk:mb:588' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:572569', 14, 1482888, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7591', 'mk:mb:572569', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7591', 'url', 'https://www.webtoons.com/en/comedy/change-your-story/list?title_no=7591'))
         WHERE miko_work_id = 'mk:mb:572569' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250819_94/1755535695863OzNkK_JPEG/Thumb_Poster_7591.jpg'
             WHERE miko_work_id = 'mk:mb:572569' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:516537', 15, 928453, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9299', 'mk:mb:516537', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9299', 'url', 'https://www.webtoons.com/en/action/fog-land/list?title_no=9299'))
         WHERE miko_work_id = 'mk:mb:516537' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:516537' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260303_87/1772529537244BXF3q_JPEG/3Thumb_Poster_9299.jpg'
             WHERE miko_work_id = 'mk:mb:516537' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:576606', 16, 727524, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10036', 'mk:mb:576606', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10036', 'url', 'https://www.webtoons.com/en/thriller/i-dare-you/list?title_no=10036'))
         WHERE miko_work_id = 'mk:mb:576606' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:576606' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260505_74/1777925469986bEdg0_JPEG/4Thumb_Poster_10036.jpg'
             WHERE miko_work_id = 'mk:mb:576606' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:76921', 17, 7412364, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6278', 'mk:mb:76921', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6278', 'url', 'https://www.webtoons.com/en/romance/love-4-a-walk/list?title_no=6278'))
         WHERE miko_work_id = 'mk:mb:76921' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251024_109/1761271188153NKpoe_JPEG/Thumb_Poster_6278.jpg'
             WHERE miko_work_id = 'mk:mb:76921' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:6968', 18, 2417288, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7261', 'mk:mb:6968', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7261', 'url', 'https://www.webtoons.com/en/action/the-regressed-mercenary-has-a-plan/list?title_no=7261'))
         WHERE miko_work_id = 'mk:mb:6968' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251018_97/1760726463264HLT1r_JPEG/Thumb_Poster_7261.jpg'
             WHERE miko_work_id = 'mk:mb:6968' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:70901', 19, 26781241, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2616', 'mk:mb:70901', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2616', 'url', 'https://www.webtoons.com/en/slice-of-life/boyfriends/list?title_no=2616'))
         WHERE miko_work_id = 'mk:mb:70901' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260107_287/1767738614069fHGb9_JPEG/Thumb_Poster_2616.jpg'
             WHERE miko_work_id = 'mk:mb:70901' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:5307', 20, 2718529, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6465', 'mk:mb:5307', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6465', 'url', 'https://www.webtoons.com/en/fantasy/the-extras-academy-survival-guide/list?title_no=6465'))
         WHERE miko_work_id = 'mk:mb:5307' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260224_62/1771871625900u9uS8_JPEG/7Thumb_Poster_6465.jpg'
             WHERE miko_work_id = 'mk:mb:5307' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:565516', 21, 238570, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9578', 'mk:mb:565516', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9578', 'url', 'https://www.webtoons.com/en/romance/sweet-romance-spicy-roommates/list?title_no=9578'))
         WHERE miko_work_id = 'mk:mb:565516' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:565516' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260223_221/17718141445867C9sh_JPEG/2Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:565516' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:6459', 22, 6051570, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3349', 'mk:mb:6459', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3349', 'url', 'https://www.webtoons.com/en/fantasy/the-lazy-lord-masters-the-sword/list?title_no=3349'))
         WHERE miko_work_id = 'mk:mb:6459' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260903_117/1788372850993by6hI_JPEG/1Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:6459' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:3629', 24, 4285106, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5656', 'mk:mb:3629', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5656', 'url', 'https://www.webtoons.com/en/fantasy/the-top-dungeon-farmer/list?title_no=5656'))
         WHERE miko_work_id = 'mk:mb:3629' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260212_180/1770887553105xct5j_JPEG/5Thumb_Poster_5656.jpg'
             WHERE miko_work_id = 'mk:mb:3629' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:571771', 25, 304574, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9786', 'mk:mb:571771', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9786', 'url', 'https://www.webtoons.com/en/action/the-glutton-devourer-of-kings/list?title_no=9786'))
         WHERE miko_work_id = 'mk:mb:571771' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260317_200/1773682723606YX3qF_JPEG/6Thumb_Poster_9786.jpg'
             WHERE miko_work_id = 'mk:mb:571771' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:31017', 27, 29296077, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1468', 'mk:mb:31017', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1468', 'url', 'https://www.webtoons.com/en/romance/subzero/list?title_no=1468'))
         WHERE miko_work_id = 'mk:mb:31017' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:31017' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250703_81/1751489277713osNX6_JPEG/Thumb_Poster_6594.jpg'
             WHERE miko_work_id = 'mk:mb:31017' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:2789', 28, 2718998, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7492', 'mk:mb:2789', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7492', 'url', 'https://www.webtoons.com/en/romance/the-beloved-incompetent-stepmom/list?title_no=7492'))
         WHERE miko_work_id = 'mk:mb:2789' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250326_90/17429908367886PMy0_JPEG/Thumb_Poster_7492.jpg'
             WHERE miko_work_id = 'mk:mb:2789' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:604493', 29, 22569, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '11210', 'mk:mb:604493', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '11210', 'url', 'https://www.webtoons.com/en/action/the-warriors-ballad/list?title_no=11210'))
         WHERE miko_work_id = 'mk:mb:604493' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260918_2/1789732676876U8v3C_JPEG/3Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:604493' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:373630', 31, 79177827, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '958', 'mk:mb:373630', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '958', 'url', 'https://www.webtoons.com/en/slice-of-life/my-giant-nerd-boyfriend/list?title_no=958'))
         WHERE miko_work_id = 'mk:mb:373630' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251118_197/1763421148017h0bc9_JPEG/Thumb_Poster_958.jpg'
             WHERE miko_work_id = 'mk:mb:373630' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:8805', 32, 71022220, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '679', 'mk:mb:8805', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '679', 'url', 'https://www.webtoons.com/en/super-hero/unordinary/list?title_no=679'))
         WHERE miko_work_id = 'mk:mb:8805' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250730_49/1753832944376xEinr_JPEG/unOrdinary_Thumb_Poster_250721.jpg'
             WHERE miko_work_id = 'mk:mb:8805' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:1536', 33, 59205545, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1049', 'mk:mb:1536', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1049', 'url', 'https://www.webtoons.com/en/drama/lookism/list?title_no=1049'))
         WHERE miko_work_id = 'mk:mb:1536' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:1536' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_168/17386458815964J4hG_JPEG/1049.jpg'
             WHERE miko_work_id = 'mk:mb:1536' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:556680', 34, 51437641, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '199', 'mk:mb:556680', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '199', 'url', 'https://www.webtoons.com/en/slice-of-life/bluechair/list?title_no=199'))
         WHERE miko_work_id = 'mk:mb:556680' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_111/1738645847318zqrTC_JPEG/199.jpg'
             WHERE miko_work_id = 'mk:mb:556680' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:25721', 35, 40122925, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '986', 'mk:mb:25721', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '986', 'url', 'https://www.webtoons.com/en/drama/i-love-yoo/list?title_no=986'))
         WHERE miko_work_id = 'mk:mb:25721' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251118_74/17634215100065nlfb_JPEG/Thumb_Poster_986.jpg'
             WHERE miko_work_id = 'mk:mb:25721' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:25710', 36, 25696611, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1420', 'mk:mb:25710', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1420', 'url', 'https://www.webtoons.com/en/drama/odd-girl-out/list?title_no=1420'))
         WHERE miko_work_id = 'mk:mb:25710' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251111_220/17628253863843FEUu_JPEG/4) Thumb_Poster_1420.jpg'
             WHERE miko_work_id = 'mk:mb:25710' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:36050', 38, 24541826, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1537', 'mk:mb:36050', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1537', 'url', 'https://www.webtoons.com/en/comedy/cursed-princess-club/list?title_no=1537'))
         WHERE miko_work_id = 'mk:mb:36050' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_14/1738646003049B8065_JPEG/1537.jpg'
             WHERE miko_work_id = 'mk:mb:36050' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:25439', 39, 20065140, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '478', 'mk:mb:25439', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '478', 'url', 'https://www.webtoons.com/en/slice-of-life/yumi-cell/list?title_no=478'))
         WHERE miko_work_id = 'mk:mb:25439' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_17/1738645861273y1CEQ_JPEG/478.jpg'
             WHERE miko_work_id = 'mk:mb:25439' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:26401', 40, 19448893, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1285', 'mk:mb:26401', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1285', 'url', 'https://www.webtoons.com/en/thriller/sweethome/list?title_no=1285'))
         WHERE miko_work_id = 'mk:mb:26401' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:26401' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_282/1738645892388AhBYI_JPEG/1285.jpg'
             WHERE miko_work_id = 'mk:mb:26401' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:8113', 41, 17709386, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4465', 'mk:mb:8113', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4465', 'url', 'https://www.webtoons.com/en/romance/operation-true-love/list?title_no=4465'))
         WHERE miko_work_id = 'mk:mb:8113' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260930_109/1790707959300tKyTk_JPEG/2Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:8113' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:45850', 42, 17338140, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1621', 'mk:mb:45850', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1621', 'url', 'https://www.webtoons.com/en/mystery/purple-hyacinth/list?title_no=1621'))
         WHERE miko_work_id = 'mk:mb:45850' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:45850' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_285/1738646012744DVSUz_JPEG/1621.jpg'
             WHERE miko_work_id = 'mk:mb:45850' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:372674', 43, 15132894, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3180', 'mk:mb:372674', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3180', 'url', 'https://www.webtoons.com/en/slice-of-life/batman-wayne-family-adventures/list?title_no=3180'))
         WHERE miko_work_id = 'mk:mb:372674' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_19/1738718670675e8Srk_JPEG/3180.jpg'
             WHERE miko_work_id = 'mk:mb:372674' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:39593', 44, 14840090, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1697', 'mk:mb:39593', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1697', 'url', 'https://www.webtoons.com/en/supernatural/im-the-grim-reaper/list?title_no=1697'))
         WHERE miko_work_id = 'mk:mb:39593' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:39593' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_156/1738646021647P1UQB_JPEG/1697.jpg'
             WHERE miko_work_id = 'mk:mb:39593' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:534100', 45, 14813239, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2159', 'mk:mb:534100', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2159', 'url', 'https://www.webtoons.com/en/fantasy/suitor-armor/list?title_no=2159'))
         WHERE miko_work_id = 'mk:mb:534100' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20261001_291/1790813269686fnHNm_JPEG/5Thumb_Poster_2159.jpg'
             WHERE miko_work_id = 'mk:mb:534100' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:372043', 46, 14777010, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4047', 'mk:mb:372043', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4047', 'url', 'https://www.webtoons.com/en/slice-of-life/to-the-stars-and-back/list?title_no=4047'))
         WHERE miko_work_id = 'mk:mb:372043' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251216_17/1765854757734IRUkP_JPEG/4) Thumb_Poster_4047.jpg'
             WHERE miko_work_id = 'mk:mb:372043' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:374686', 47, 14505378, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2723', 'mk:mb:374686', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2723', 'url', 'https://www.webtoons.com/en/comedy/high-class-homos/list?title_no=2723'))
         WHERE miko_work_id = 'mk:mb:374686' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260730_265/1785368076015bhdKh_JPEG/2HighClassHomos_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:374686' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:64997', 48, 14028558, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4089', 'mk:mb:64997', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4089', 'url', 'https://www.webtoons.com/en/slice-of-life/daybreak/list?title_no=4089'))
         WHERE miko_work_id = 'mk:mb:64997' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260729_96/1785271505566n8tT9_JPEG/6Thumb_Poster_4089.jpg'
             WHERE miko_work_id = 'mk:mb:64997' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:37897', 49, 13978997, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1262', 'mk:mb:37897', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1262', 'url', 'https://www.webtoons.com/en/supernatural/unholy-blood/list?title_no=1262'))
         WHERE miko_work_id = 'mk:mb:37897' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:37897' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_140/1738645889132F7F22_JPEG/1262.jpg'
             WHERE miko_work_id = 'mk:mb:37897' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:29690', 50, 13773172, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1695', 'mk:mb:29690', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1695', 'url', 'https://www.webtoons.com/en/supernatural/devil-no-4/list?title_no=1695'))
         WHERE miko_work_id = 'mk:mb:29690' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_18/1738646021612AdotO_JPEG/1695.jpg'
             WHERE miko_work_id = 'mk:mb:29690' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:3292', 51, 13688777, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1498', 'mk:mb:3292', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1498', 'url', 'https://www.webtoons.com/en/comedy/love-advice/list?title_no=1498'))
         WHERE miko_work_id = 'mk:mb:3292' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_85/17386459125332s9ef_JPEG/1498.jpg'
             WHERE miko_work_id = 'mk:mb:3292' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:36685', 52, 13076376, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1756', 'mk:mb:36685', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1756', 'url', 'https://www.webtoons.com/en/thriller/not-even-bones/list?title_no=1756'))
         WHERE miko_work_id = 'mk:mb:36685' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:36685' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_87/1738646024876NCYIJ_JPEG/1756.jpg'
             WHERE miko_work_id = 'mk:mb:36685' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:795', 53, 12230974, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2480', 'mk:mb:795', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2480', 'url', 'https://www.webtoons.com/en/action/jungle-juice/list?title_no=2480'))
         WHERE miko_work_id = 'mk:mb:795' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_84/17386460542331IDQz_JPEG/2480.jpg'
             WHERE miko_work_id = 'mk:mb:795' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:1557', 54, 12051399, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3414', 'mk:mb:1557', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3414', 'url', 'https://www.webtoons.com/en/supernatural/clinic-of-horrors/list?title_no=3414'))
         WHERE miko_work_id = 'mk:mb:1557' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251209_51/1765239776409ejCAj_PNG/4)-Thumb_Poster_3414.png'
             WHERE miko_work_id = 'mk:mb:1557' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:38335', 55, 11886108, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2027', 'mk:mb:38335', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2027', 'url', 'https://www.webtoons.com/en/sports/the-boxer/list?title_no=2027'))
         WHERE miko_work_id = 'mk:mb:38335' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:38335' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_229/1738646040437AnWgK_JPEG/2027.jpg'
             WHERE miko_work_id = 'mk:mb:38335' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:6731', 56, 10870660, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2857', 'mk:mb:6731', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2857', 'url', 'https://www.webtoons.com/en/action/villain-to-kill/list?title_no=2857'))
         WHERE miko_work_id = 'mk:mb:6731' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_287/1738718529540vYc6g_JPEG/2857.jpg'
             WHERE miko_work_id = 'mk:mb:6731' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:55414', 57, 10596176, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4886', 'mk:mb:55414', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4886', 'url', 'https://www.webtoons.com/en/fantasy/im-the-queen-in-this-life/list?title_no=4886'))
         WHERE miko_work_id = 'mk:mb:55414' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:55414' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251108_149/1762549068848VaNDd_JPEG/4) Thumb_Poster_4886.jpg'
             WHERE miko_work_id = 'mk:mb:55414' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:29293', 58, 10373206, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '68', 'mk:mb:29293', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '68', 'url', 'https://www.webtoons.com/en/horror/tales-of-the-unusual/list?title_no=68'))
         WHERE miko_work_id = 'mk:mb:29293' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_184/1738645830206LshDB_JPEG/68.jpg'
             WHERE miko_work_id = 'mk:mb:29293' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:77860', 59, 10123733, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2964', 'mk:mb:77860', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2964', 'url', 'https://www.webtoons.com/en/fantasy/morgana-and-oz/list?title_no=2964'))
         WHERE miko_work_id = 'mk:mb:77860' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250811_5/1754847189235xJRyK_JPEG/Thumb_Poster_2964.jpg'
             WHERE miko_work_id = 'mk:mb:77860' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:528865', 60, 9374274, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '400', 'mk:mb:528865', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '400', 'url', 'https://www.webtoons.com/en/sf/space-boy/list?title_no=400'))
         WHERE miko_work_id = 'mk:mb:528865' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260805_170/1785876674675YlX43_JPEG/8SpaceBoy_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:528865' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:379242', 61, 8538394, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1895', 'mk:mb:379242', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1895', 'url', 'https://www.webtoons.com/en/sf/city-of-blank/list?title_no=1895'))
         WHERE miko_work_id = 'mk:mb:379242' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:379242' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250821_145/1755710261452gQcL3_JPEG/Thumb_Poster_1895.jpg'
             WHERE miko_work_id = 'mk:mb:379242' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:5911', 62, 8184260, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4011', 'mk:mb:5911', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4011', 'url', 'https://www.webtoons.com/en/action/the-world-after-the-fall/list?title_no=4011'))
         WHERE miko_work_id = 'mk:mb:5911' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:5911' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251108_179/1762550759532H9zms_JPEG/4) Thumb_Poster_4011.jpg'
             WHERE miko_work_id = 'mk:mb:5911' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:21417', 63, 8153808, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '485', 'mk:mb:21417', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '485', 'url', 'https://www.webtoons.com/en/thriller/bastard/list?title_no=485'))
         WHERE miko_work_id = 'mk:mb:21417' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:21417' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_240/1738645862365qDQDr_JPEG/485.jpg'
             WHERE miko_work_id = 'mk:mb:21417' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:1746', 64, 8026291, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2849', 'mk:mb:1746', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2849', 'url', 'https://www.webtoons.com/en/action/return-of-the-blossoming-blade/list?title_no=2849'))
         WHERE miko_work_id = 'mk:mb:1746' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260413_169/1776083474128FEc3K_JPEG/7Thumb_Poster_2849.jpg'
             WHERE miko_work_id = 'mk:mb:1746' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:49861', 65, 7281912, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3963', 'mk:mb:49861', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3963', 'url', 'https://www.webtoons.com/en/action/my-s-class-hunters/list?title_no=3963'))
         WHERE miko_work_id = 'mk:mb:49861' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260609_290/1780947460493K1cSq_JPEG/1Thumb_Poster_3963.jpg'
             WHERE miko_work_id = 'mk:mb:49861' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:514', 66, 6032902, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5753', 'mk:mb:514', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5753', 'url', 'https://www.webtoons.com/en/romance/iseops-romance/list?title_no=5753'))
         WHERE miko_work_id = 'mk:mb:514' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:514' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260128_41/1769554651658iQv60_JPEG/2Thumb_Poster_5753.jpg'
             WHERE miko_work_id = 'mk:mb:514' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:373071', 67, 5993805, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4841', 'mk:mb:373071', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4841', 'url', 'https://www.webtoons.com/en/slice-of-life/vampire-husband/list?title_no=4841'))
         WHERE miko_work_id = 'mk:mb:373071' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251205_222/1764885314153HDDRn_JPEG/4) Thumb_Poster_4841.jpg'
             WHERE miko_work_id = 'mk:mb:373071' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:39176', 68, 5946430, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1572', 'mk:mb:39176', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1572', 'url', 'https://www.webtoons.com/en/sports/no-scope/list?title_no=1572'))
         WHERE miko_work_id = 'mk:mb:39176' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:39176' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_164/1738646007874Cd84R_JPEG/1572.jpg'
             WHERE miko_work_id = 'mk:mb:39176' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:46503', 69, 5864025, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2607', 'mk:mb:46503', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2607', 'url', 'https://www.webtoons.com/en/horror/survive-romance/list?title_no=2607'))
         WHERE miko_work_id = 'mk:mb:46503' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:46503' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_273/1738646062610SXeRm_JPEG/2607.jpg'
             WHERE miko_work_id = 'mk:mb:46503' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:25482', 70, 5437912, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1307', 'mk:mb:25482', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1307', 'url', 'https://www.webtoons.com/en/horror/ghost-teller/list?title_no=1307'))
         WHERE miko_work_id = 'mk:mb:25482' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:25482' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_162/1738645893552CcW5g_JPEG/1307.jpg'
             WHERE miko_work_id = 'mk:mb:25482' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:49377', 71, 5412913, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3671', 'mk:mb:49377', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3671', 'url', 'https://www.webtoons.com/en/historical/return-of-the-mad-demon/list?title_no=3671'))
         WHERE miko_work_id = 'mk:mb:49377' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:49377' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260815_258/17867556932485gnL5_JPEG/4Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:49377' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:32618', 72, 5018729, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1164', 'mk:mb:32618', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1164', 'url', 'https://www.webtoons.com/en/supernatural/fluffy-boyfriend/list?title_no=1164'))
         WHERE miko_work_id = 'mk:mb:32618' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_11/1738645884041EoswF_JPEG/1164.jpg'
             WHERE miko_work_id = 'mk:mb:32618' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:374070', 73, 4928884, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5878', 'mk:mb:374070', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5878', 'url', 'https://www.webtoons.com/en/romance/bailin-and-li-yun/list?title_no=5878'))
         WHERE miko_work_id = 'mk:mb:374070' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_113/1738719723384810IV_JPEG/5878.jpg'
             WHERE miko_work_id = 'mk:mb:374070' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:3771', 74, 4823112, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5037', 'mk:mb:3771', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5037', 'url', 'https://www.webtoons.com/en/drama/serena/list?title_no=5037'))
         WHERE miko_work_id = 'mk:mb:3771' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:3771' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260804_122/1785798209317O1Wf0_JPEG/4Thumb_Poster_5037.jpg'
             WHERE miko_work_id = 'mk:mb:3771' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:47590', 75, 4669311, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2534', 'mk:mb:47590', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2534', 'url', 'https://www.webtoons.com/en/thriller/shotgun-boy/list?title_no=2534'))
         WHERE miko_work_id = 'mk:mb:47590' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:47590' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_32/1738646060662WOoXy_JPEG/2534.jpg'
             WHERE miko_work_id = 'mk:mb:47590' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:24075', 76, 4560308, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '604', 'mk:mb:24075', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '604', 'url', 'https://www.webtoons.com/en/sports/ecstasy-hearts/list?title_no=604'))
         WHERE miko_work_id = 'mk:mb:24075' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_251/1738645866918IXXIU_JPEG/604.jpg'
             WHERE miko_work_id = 'mk:mb:24075' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:32348', 77, 4464607, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '959', 'mk:mb:32348', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '959', 'url', 'https://www.webtoons.com/en/supernatural/rise-from-ashes/list?title_no=959'))
         WHERE miko_work_id = 'mk:mb:32348' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_93/1738645878463s3YfQ_JPEG/959.jpg'
             WHERE miko_work_id = 'mk:mb:32348' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:38973', 78, 4431300, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1946', 'mk:mb:38973', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1946', 'url', 'https://www.webtoons.com/en/sf/in-the-bleak-midwinter/list?title_no=1946'))
         WHERE miko_work_id = 'mk:mb:38973' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250910_282/1757438465572cFqMI_JPEG/Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:38973' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:52908', 79, 3932099, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4286', 'mk:mb:52908', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4286', 'url', 'https://www.webtoons.com/en/comedy/99-reinforced-wooden-stick/list?title_no=4286'))
         WHERE miko_work_id = 'mk:mb:52908' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:52908' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251025_216/1761331934832eRt0O_JPEG/Thumb_Poster_4286.jpg'
             WHERE miko_work_id = 'mk:mb:52908' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:72', 80, 3899851, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5752', 'mk:mb:72', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5752', 'url', 'https://www.webtoons.com/en/fantasy/the-academys-genius-swordsman/list?title_no=5752'))
         WHERE miko_work_id = 'mk:mb:72' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:72' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260616_78/1781554206466d3cYu_JPEG/8Thumb_Poster_5752.jpg'
             WHERE miko_work_id = 'mk:mb:72' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:41110', 81, 3877074, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2053', 'mk:mb:41110', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2053', 'url', 'https://www.webtoons.com/en/mystery/parallelcity/list?title_no=2053'))
         WHERE miko_work_id = 'mk:mb:41110' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:41110' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_205/1738646040701kUMFB_JPEG/2053.jpg'
             WHERE miko_work_id = 'mk:mb:41110' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:38838', 82, 3830511, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2128', 'mk:mb:38838', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2128', 'url', 'https://www.webtoons.com/en/supernatural/mystical/list?title_no=2128'))
         WHERE miko_work_id = 'mk:mb:38838' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_107/1738646043100OuNJn_JPEG/2128.jpg'
             WHERE miko_work_id = 'mk:mb:38838' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:798', 83, 3770002, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6107', 'mk:mb:798', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6107', 'url', 'https://www.webtoons.com/en/fantasy/the-reborn-young-lord-is-an-assassin/list?title_no=6107'))
         WHERE miko_work_id = 'mk:mb:798' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251025_248/1761328285275sNnf6_JPEG/Thumb_Poster_6107.jpg'
             WHERE miko_work_id = 'mk:mb:798' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:6030', 84, 3762380, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6080', 'mk:mb:6030', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6080', 'url', 'https://www.webtoons.com/en/comedy/stalker-x-stalker/list?title_no=6080'))
         WHERE miko_work_id = 'mk:mb:6030' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:6030' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_240/1738719668499GGI7l_JPEG/6080.jpg'
             WHERE miko_work_id = 'mk:mb:6030' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:2337', 85, 3627897, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '371', 'mk:mb:2337', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '371', 'url', 'https://www.webtoons.com/en/mystery/dr-frost/list?title_no=371'))
         WHERE miko_work_id = 'mk:mb:2337' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_178/1738645852189TCKkc_JPEG/371.jpg'
             WHERE miko_work_id = 'mk:mb:2337' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:534428', 86, 3616361, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1480', 'mk:mb:534428', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1480', 'url', 'https://www.webtoons.com/en/sf/seed/list?title_no=1480'))
         WHERE miko_work_id = 'mk:mb:534428' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_149/1738645909843gGAXV_JPEG/1480.jpg'
             WHERE miko_work_id = 'mk:mb:534428' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:7666', 87, 3568960, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '700', 'mk:mb:7666', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '700', 'url', 'https://www.webtoons.com/en/sf/nano-list/list?title_no=700'))
         WHERE miko_work_id = 'mk:mb:7666' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_68/1738645874741KI06W_JPEG/700.jpg'
             WHERE miko_work_id = 'mk:mb:7666' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:33397', 88, 3538564, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1607', 'mk:mb:33397', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1607', 'url', 'https://www.webtoons.com/en/horror/uriah/list?title_no=1607'))
         WHERE miko_work_id = 'mk:mb:33397' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:33397' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250731_248/17539252271458HgoW_JPEG/Thumb_Poster_1607.jpg'
             WHERE miko_work_id = 'mk:mb:33397' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:15768', 89, 3362146, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '98', 'mk:mb:15768', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '98', 'url', 'https://www.webtoons.com/en/super-hero/i-dont-want-this-kind-of-hero/list?title_no=98'))
         WHERE miko_work_id = 'mk:mb:15768' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_100/1738645844488slwPj_JPEG/98.jpg'
             WHERE miko_work_id = 'mk:mb:15768' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:15151', 90, 3348554, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5912', 'mk:mb:15151', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5912', 'url', 'https://www.webtoons.com/en/comedy/cinderella-boy/list?title_no=5912'))
         WHERE miko_work_id = 'mk:mb:15151' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260410_267/1775780692477NIojy_JPEG/3Cinderella_Boy_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:15151' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:303382', 91, 3231305, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1483', 'mk:mb:303382', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1483', 'url', 'https://www.webtoons.com/en/super-hero/urban-animal/list?title_no=1483'))
         WHERE miko_work_id = 'mk:mb:303382' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260106_34/1767661665201mVGrO_JPEG/4) Thumb_Poster_1483.jpg'
             WHERE miko_work_id = 'mk:mb:303382' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:24016', 92, 3210194, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1185', 'mk:mb:24016', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1185', 'url', 'https://www.webtoons.com/en/supernatural/my-boo/list?title_no=1185'))
         WHERE miko_work_id = 'mk:mb:24016' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_237/17386458839310ngsx_JPEG/1185.jpg'
             WHERE miko_work_id = 'mk:mb:24016' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:79020', 93, 3127116, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6402', 'mk:mb:79020', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6402', 'url', 'https://www.webtoons.com/en/comedy/vampire-family/list?title_no=6402'))
         WHERE miko_work_id = 'mk:mb:79020' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260617_104/1781646151607Nk3JF_JPEG/6Thumb_Poster_6402.jpg'
             WHERE miko_work_id = 'mk:mb:79020' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:58016', 95, 2910310, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3763', 'mk:mb:58016', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3763', 'url', 'https://www.webtoons.com/en/supernatural/to-tame-a-fire/list?title_no=3763'))
         WHERE miko_work_id = 'mk:mb:58016' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260512_222/1778522347403AEe1H_JPEG/5Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:58016' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:47203', 96, 2845410, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4635', 'mk:mb:47203', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4635', 'url', 'https://www.webtoons.com/en/thriller/trapped/list?title_no=4635'))
         WHERE miko_work_id = 'mk:mb:47203' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:47203' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_288/1738719308684YN1Xt_JPEG/4635.jpg'
             WHERE miko_work_id = 'mk:mb:47203' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:43850', 98, 2747723, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2529', 'mk:mb:43850', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2529', 'url', 'https://www.webtoons.com/en/supernatural/devilish-romance/list?title_no=2529'))
         WHERE miko_work_id = 'mk:mb:43850' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:43850' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_272/1738646057333GATGF_JPEG/2529.jpg'
             WHERE miko_work_id = 'mk:mb:43850' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:46123', 99, 2705879, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1659', 'mk:mb:46123', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1659', 'url', 'https://www.webtoons.com/en/super-hero/the-fever-king/list?title_no=1659'))
         WHERE miko_work_id = 'mk:mb:46123' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:46123' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_71/1738646017056k4t1b_JPEG/1659.jpg'
             WHERE miko_work_id = 'mk:mb:46123' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:7422', 100, 2645624, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3669', 'mk:mb:7422', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3669', 'url', 'https://www.webtoons.com/en/sports/double-click/list?title_no=3669'))
         WHERE miko_work_id = 'mk:mb:7422' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260523_262/177948520167907iXB_JPEG/1Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:7422' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:8277', 102, 2566946, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7109', 'mk:mb:8277', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7109', 'url', 'https://www.webtoons.com/en/romance/duchess-in-ruins/list?title_no=7109'))
         WHERE miko_work_id = 'mk:mb:8277' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251011_44/1760144522491g8iYf_PNG/Thumb_Poster_7109.png'
             WHERE miko_work_id = 'mk:mb:8277' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:7971', 103, 2539457, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5100', 'mk:mb:7971', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5100', 'url', 'https://www.webtoons.com/en/action/absolute-sword-sense/list?title_no=5100'))
         WHERE miko_work_id = 'mk:mb:7971' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:7971' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_262/1738719678455dVNbj_JPEG/5100.jpg'
             WHERE miko_work_id = 'mk:mb:7971' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:380453', 105, 2462662, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5969', 'mk:mb:380453', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5969', 'url', 'https://www.webtoons.com/en/comedy/paperteeth/list?title_no=5969'))
         WHERE miko_work_id = 'mk:mb:380453' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_35/17387196436099uK6k_JPEG/5969.jpg'
             WHERE miko_work_id = 'mk:mb:380453' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:3076', 107, 2424981, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6752', 'mk:mb:3076', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6752', 'url', 'https://www.webtoons.com/en/drama/behind-her-highnesss-smile/list?title_no=6752'))
         WHERE miko_work_id = 'mk:mb:3076' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:3076' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251108_173/1762556489402hTNLw_JPEG/4) Thumb_Poster_6752.jpg'
             WHERE miko_work_id = 'mk:mb:3076' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:30461', 108, 2422467, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1021', 'mk:mb:30461', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1021', 'url', 'https://www.webtoons.com/en/horror/melvinas-therapy/list?title_no=1021'))
         WHERE miko_work_id = 'mk:mb:30461' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:30461' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_126/17386458811201UOhY_JPEG/1021.jpg'
             WHERE miko_work_id = 'mk:mb:30461' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:528882', 109, 2417812, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1893', 'mk:mb:528882', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1893', 'url', 'https://www.webtoons.com/en/horror/gremoryland/list?title_no=1893'))
         WHERE miko_work_id = 'mk:mb:528882' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:528882' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_157/17386460353853hqlx_JPEG/1893.jpg'
             WHERE miko_work_id = 'mk:mb:528882' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:5031', 110, 2272421, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3577', 'mk:mb:5031', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3577', 'url', 'https://www.webtoons.com/en/supernatural/flawed-almighty/list?title_no=3577'))
         WHERE miko_work_id = 'mk:mb:5031' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:5031' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_128/1738719021522mlTCY_JPEG/3577.jpg'
             WHERE miko_work_id = 'mk:mb:5031' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:52916', 111, 2217902, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4353', 'mk:mb:52916', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4353', 'url', 'https://www.webtoons.com/en/drama/my-dud-to-stud-boyfriend/list?title_no=4353'))
         WHERE miko_work_id = 'mk:mb:52916' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251111_74/1762825242595FX0Au_JPEG/Thumb_Poster_4353.jpg'
             WHERE miko_work_id = 'mk:mb:52916' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:319067', 112, 2185106, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3182', 'mk:mb:319067', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3182', 'url', 'https://www.webtoons.com/en/supernatural/la-tenebrosa-evelyne/list?title_no=3182'))
         WHERE miko_work_id = 'mk:mb:319067' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_258/1738718668459wDJA6_JPEG/3182.jpg'
             WHERE miko_work_id = 'mk:mb:319067' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:86292', 113, 2179467, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6205', 'mk:mb:86292', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6205', 'url', 'https://www.webtoons.com/en/graphic-novel/avatar-the-last-airbender/list?title_no=6205'))
         WHERE miko_work_id = 'mk:mb:86292' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_215/1738719499588SlTcN_JPEG/6205.jpg'
             WHERE miko_work_id = 'mk:mb:86292' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:192', 115, 2133994, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2850', 'mk:mb:192', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2850', 'url', 'https://www.webtoons.com/en/sports/hanlim-gym/list?title_no=2850'))
         WHERE miko_work_id = 'mk:mb:192' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:192' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_29/17387185321102dm51_PNG/2850.png'
             WHERE miko_work_id = 'mk:mb:192' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:9098', 116, 2099827, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5734', 'mk:mb:9098', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5734', 'url', 'https://www.webtoons.com/en/drama/like-mother-like-daughter/list?title_no=5734'))
         WHERE miko_work_id = 'mk:mb:9098' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251108_160/1762550579996P7v5s_JPEG/Thumb_Poster_5734.jpg'
             WHERE miko_work_id = 'mk:mb:9098' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:376198', 117, 2087473, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6020', 'mk:mb:376198', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6020', 'url', 'https://www.webtoons.com/en/comedy/so-not-my-type/list?title_no=6020'))
         WHERE miko_work_id = 'mk:mb:376198' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260421_213/1776719804864jGV8c_JPEG/4Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:376198' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:33986', 118, 2049547, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2275', 'mk:mb:33986', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2275', 'url', 'https://www.webtoons.com/en/thriller/pigpen/list?title_no=2275'))
         WHERE miko_work_id = 'mk:mb:33986' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:33986' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_88/1738646047232Xmt8d_JPEG/2275.jpg'
             WHERE miko_work_id = 'mk:mb:33986' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:189', 119, 2039016, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4956', 'mk:mb:189', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4956', 'url', 'https://www.webtoons.com/en/drama/reborn-rich/list?title_no=4956'))
         WHERE miko_work_id = 'mk:mb:189' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251111_216/1762825294285NeSDB_JPEG/Thumb_Poster_4956.jpg'
             WHERE miko_work_id = 'mk:mb:189' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:2794', 121, 2012367, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7121', 'mk:mb:2794', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7121', 'url', 'https://www.webtoons.com/en/romance/a-savage-proposal/list?title_no=7121'))
         WHERE miko_work_id = 'mk:mb:2794' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_272/17386461436189Ou2D_JPEG/7121.jpg'
             WHERE miko_work_id = 'mk:mb:2794' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:21049', 122, 1981995, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '75', 'mk:mb:21049', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '75', 'url', 'https://www.webtoons.com/en/horror/distant-sky/list?title_no=75'))
         WHERE miko_work_id = 'mk:mb:21049' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:21049' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_177/1738645831166OVDmo_JPEG/75.jpg'
             WHERE miko_work_id = 'mk:mb:21049' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:7218', 123, 1976341, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6993', 'mk:mb:7218', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6993', 'url', 'https://www.webtoons.com/en/fantasy/the-flower-that-swallowed-the-beast/list?title_no=6993'))
         WHERE miko_work_id = 'mk:mb:7218' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:7218' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260303_126/1772528425078zTPAR_JPEG/4Thumb_Poster_6993.jpg'
             WHERE miko_work_id = 'mk:mb:7218' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:45119', 124, 1968469, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2876', 'mk:mb:45119', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2876', 'url', 'https://www.webtoons.com/en/drama/a-mans-man/list?title_no=2876'))
         WHERE miko_work_id = 'mk:mb:45119' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250814_33/17551048266381OVsc_JPEG/4) Thumb_Poster_2876.jpg'
             WHERE miko_work_id = 'mk:mb:45119' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:5920', 125, 1946686, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3440', 'mk:mb:5920', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3440', 'url', 'https://www.webtoons.com/en/mystery/chasing-tails/list?title_no=3440'))
         WHERE miko_work_id = 'mk:mb:5920' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:5920' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_283/1738718994068MVlWx_JPEG/3440.jpg'
             WHERE miko_work_id = 'mk:mb:5920' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:557437', 126, 1942322, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2191', 'mk:mb:557437', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2191', 'url', 'https://www.webtoons.com/en/heartwarming/heart-anthology/list?title_no=2191'))
         WHERE miko_work_id = 'mk:mb:557437' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_24/1738646045358XYM4y_JPEG/2191.jpg'
             WHERE miko_work_id = 'mk:mb:557437' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:81015', 127, 1930961, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6079', 'mk:mb:81015', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6079', 'url', 'https://www.webtoons.com/en/action/paranoid-mage/list?title_no=6079'))
         WHERE miko_work_id = 'mk:mb:81015' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251205_283/1764885503070pgw9H_JPEG/4) Thumb_Poster_6079.jpg'
             WHERE miko_work_id = 'mk:mb:81015' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:427510', 128, 1911507, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5974', 'mk:mb:427510', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5974', 'url', 'https://www.webtoons.com/en/action/hardcore-leveling-warrior-earth-game/list?title_no=5974'))
         WHERE miko_work_id = 'mk:mb:427510' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_59/1738719564367mm351_JPEG/5974.jpg'
             WHERE miko_work_id = 'mk:mb:427510' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:498', 129, 1898257, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7697', 'mk:mb:498', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7697', 'url', 'https://www.webtoons.com/en/romance/ex-love-review/list?title_no=7697'))
         WHERE miko_work_id = 'mk:mb:498' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260701_20/1782841860818gHCjW_JPEG/5Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:498' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:528327', 130, 1829674, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1453', 'mk:mb:528327', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1453', 'url', 'https://www.webtoons.com/en/horror/witch-creek-road/list?title_no=1453'))
         WHERE miko_work_id = 'mk:mb:528327' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:528327' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_42/1738645902396hTw04_JPEG/1453.jpg'
             WHERE miko_work_id = 'mk:mb:528327' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:2686', 131, 1710498, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6851', 'mk:mb:2686', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6851', 'url', 'https://www.webtoons.com/en/comedy/introverted-us/list?title_no=6851'))
         WHERE miko_work_id = 'mk:mb:2686' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_66/17387198541140B2Ld_JPEG/6851.jpg'
             WHERE miko_work_id = 'mk:mb:2686' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:109476', 132, 1667658, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8031', 'mk:mb:109476', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8031', 'url', 'https://www.webtoons.com/en/fantasy/what-a-bountiful-harvest-demon-lord/list?title_no=8031'))
         WHERE miko_work_id = 'mk:mb:109476' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250521_82/1747761576050UKrdI_JPEG/Thumb_Poster_8031.jpg'
             WHERE miko_work_id = 'mk:mb:109476' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:43730', 133, 1636627, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2620', 'mk:mb:43730', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2620', 'url', 'https://www.webtoons.com/en/heartwarming/when-the-day-comes/list?title_no=2620'))
         WHERE miko_work_id = 'mk:mb:43730' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_135/1738646064458MMGd7_JPEG/2620.jpg'
             WHERE miko_work_id = 'mk:mb:43730' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:55382', 135, 1597819, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6679', 'mk:mb:55382', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6679', 'url', 'https://www.webtoons.com/en/fantasy/hidden-class-gravity-user/list?title_no=6679'))
         WHERE miko_work_id = 'mk:mb:55382' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250820_219/175561965738103rDE_JPEG/4) Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:55382' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:504503', 136, 1596177, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6208', 'mk:mb:504503', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6208', 'url', 'https://www.webtoons.com/en/slice-of-life/plus-one/list?title_no=6208'))
         WHERE miko_work_id = 'mk:mb:504503' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251118_84/1763421542446r0Dk4_JPEG/Thumb_Poster_6208.jpg'
             WHERE miko_work_id = 'mk:mb:504503' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:1186', 137, 1594089, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7004', 'mk:mb:1186', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7004', 'url', 'https://www.webtoons.com/en/fantasy/absolute-regression/list?title_no=7004'))
         WHERE miko_work_id = 'mk:mb:1186' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:1186' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_245/1738646137042DpYTt_JPEG/7004.jpg'
             WHERE miko_work_id = 'mk:mb:1186' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:7701', 138, 1581643, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7457', 'mk:mb:7701', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7457', 'url', 'https://www.webtoons.com/en/action/ultimate-shut-in/list?title_no=7457'))
         WHERE miko_work_id = 'mk:mb:7701' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251215_93/1765784562886oAEsC_JPEG/Thumb_Poster_7457.jpg'
             WHERE miko_work_id = 'mk:mb:7701' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:3738', 139, 1544776, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7847', 'mk:mb:3738', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7847', 'url', 'https://www.webtoons.com/en/historical/miss-pendleton/list?title_no=7847'))
         WHERE miko_work_id = 'mk:mb:3738' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250508_263/1746644337560rBbQw_JPEG/Thumb_Poster_8847.jpg'
             WHERE miko_work_id = 'mk:mb:3738' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:9005', 140, 1530362, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6535', 'mk:mb:9005', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6535', 'url', 'https://www.webtoons.com/en/fantasy/nebulas-civilization/list?title_no=6535'))
         WHERE miko_work_id = 'mk:mb:9005' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_129/1738646117303l0VTf_JPEG/6535.jpg'
             WHERE miko_work_id = 'mk:mb:9005' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:388334', 141, 1507296, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1878', 'mk:mb:388334', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1878', 'url', 'https://www.webtoons.com/en/horror/rot-and-ruin/list?title_no=1878'))
         WHERE miko_work_id = 'mk:mb:388334' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:388334' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_262/1738646032908hq9cn_JPEG/1878.jpg'
             WHERE miko_work_id = 'mk:mb:388334' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:76547', 142, 1488803, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6412', 'mk:mb:76547', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6412', 'url', 'https://www.webtoons.com/en/action/a-flame-reborn/list?title_no=6412'))
         WHERE miko_work_id = 'mk:mb:76547' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_189/1738646116314VXOUo_JPEG/6412.jpg'
             WHERE miko_work_id = 'mk:mb:76547' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:3676', 143, 1448433, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6856', 'mk:mb:3676', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6856', 'url', 'https://www.webtoons.com/en/fantasy/i-killed-the-main-player/list?title_no=6856'))
         WHERE miko_work_id = 'mk:mb:3676' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251025_122/176133201399457rwX_JPEG/Thumb_Poster_6856.jpg'
             WHERE miko_work_id = 'mk:mb:3676' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:378362', 144, 1429964, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3174', 'mk:mb:378362', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3174', 'url', 'https://www.webtoons.com/en/sf/the-last-dimension/list?title_no=3174'))
         WHERE miko_work_id = 'mk:mb:378362' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251205_115/17648853678463MSBz_PNG/4)-Thumb_Poster_3174.png'
             WHERE miko_work_id = 'mk:mb:378362' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:6597', 145, 1417836, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7566', 'mk:mb:6597', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7566', 'url', 'https://www.webtoons.com/en/drama/this-wasnt-in-my-adoption-plan/list?title_no=7566'))
         WHERE miko_work_id = 'mk:mb:6597' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260923_85/1790126172645bwGAv_JPEG/4Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:6597' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:392393', 146, 1390365, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2716', 'mk:mb:392393', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2716', 'url', 'https://www.webtoons.com/en/historical/madame-outlaw/list?title_no=2716'))
         WHERE miko_work_id = 'mk:mb:392393' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_42/17387181014567ISKv_JPEG/2716.jpg'
             WHERE miko_work_id = 'mk:mb:392393' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:512712', 147, 1380224, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7576', 'mk:mb:512712', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7576', 'url', 'https://www.webtoons.com/en/sf/hazeshift/list?title_no=7576'))
         WHERE miko_work_id = 'mk:mb:512712' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250820_29/1755648467463lTykk_JPEG/Thumb_Poster_7576.jpg'
             WHERE miko_work_id = 'mk:mb:512712' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:54628', 148, 1366005, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5419', 'mk:mb:54628', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5419', 'url', 'https://www.webtoons.com/en/historical/that-which-flows-by/list?title_no=5419'))
         WHERE miko_work_id = 'mk:mb:54628' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:54628' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251220_199/1766167893927uEP9I_JPEG/4) Thumb_Poster_5419.jpg'
             WHERE miko_work_id = 'mk:mb:54628' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:46467', 149, 1329602, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2675', 'mk:mb:46467', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2675', 'url', 'https://www.webtoons.com/en/sf/it-was-all-you/list?title_no=2675'))
         WHERE miko_work_id = 'mk:mb:46467' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_123/1738646065619hMQR3_JPEG/2675.jpg'
             WHERE miko_work_id = 'mk:mb:46467' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:40609', 150, 1306808, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1640', 'mk:mb:40609', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1640', 'url', 'https://www.webtoons.com/en/super-hero/arma/list?title_no=1640'))
         WHERE miko_work_id = 'mk:mb:40609' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:40609' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_5/17386460133419k8TY_JPEG/1640.jpg'
             WHERE miko_work_id = 'mk:mb:40609' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:2578', 151, 1300404, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7857', 'mk:mb:2578', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7857', 'url', 'https://www.webtoons.com/en/romance/my-bias-gets-on-the-last-train/list?title_no=7857'))
         WHERE miko_work_id = 'mk:mb:2578' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250512_95/1747021584941voMYR_JPEG/Thumb_Poster_7857.jpg'
             WHERE miko_work_id = 'mk:mb:2578' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:5086', 152, 1263121, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3479', 'mk:mb:5086', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3479', 'url', 'https://www.webtoons.com/en/sf/housekeeper/list?title_no=3479'))
         WHERE miko_work_id = 'mk:mb:5086' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:5086' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_60/1738718989460CuSod_JPEG/3479.jpg'
             WHERE miko_work_id = 'mk:mb:5086' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:47853', 153, 1262959, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3041', 'mk:mb:47853', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3041', 'url', 'https://www.webtoons.com/en/slice-of-life/the-dog-diaries/list?title_no=3041'))
         WHERE miko_work_id = 'mk:mb:47853' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_266/1738718685197g3i77_JPEG/3041.jpg'
             WHERE miko_work_id = 'mk:mb:47853' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:43926', 154, 1246629, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3209', 'mk:mb:43926', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3209', 'url', 'https://www.webtoons.com/en/historical/the-double-agent/list?title_no=3209'))
         WHERE miko_work_id = 'mk:mb:43926' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_111/173871866970471Xgg_JPEG/3209.jpg'
             WHERE miko_work_id = 'mk:mb:43926' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:374815', 155, 1243215, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1739', 'mk:mb:374815', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1739', 'url', 'https://www.webtoons.com/en/historical/the-weight-of-our-sky/list?title_no=1739'))
         WHERE miko_work_id = 'mk:mb:374815' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_232/1738646024188Xl1Fu_JPEG/1739.jpg'
             WHERE miko_work_id = 'mk:mb:374815' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:86879', 156, 1214316, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3910', 'mk:mb:86879', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3910', 'url', 'https://www.webtoons.com/en/slice-of-life/a-deadbeats-meal/list?title_no=3910'))
         WHERE miko_work_id = 'mk:mb:86879' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_15/1738719223780lsaNt_PNG/3910.png'
             WHERE miko_work_id = 'mk:mb:86879' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:4807', 157, 1197669, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7567', 'mk:mb:4807', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7567', 'url', 'https://www.webtoons.com/en/fantasy/the-archmages-restaurant/list?title_no=7567'))
         WHERE miko_work_id = 'mk:mb:4807' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250326_20/1742987154341Qb3b3_JPEG/Thumb_Poster_7567.jpg'
             WHERE miko_work_id = 'mk:mb:4807' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:19436', 158, 1151927, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '363', 'mk:mb:19436', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '363', 'url', 'https://www.webtoons.com/en/supernatural/witch-hunt/list?title_no=363'))
         WHERE miko_work_id = 'mk:mb:19436' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_282/1738645851367JWIgp_JPEG/363.jpg'
             WHERE miko_work_id = 'mk:mb:19436' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:46378', 159, 1139872, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2619', 'mk:mb:46378', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2619', 'url', 'https://www.webtoons.com/en/historical/the-snake-and-the-flower/list?title_no=2619'))
         WHERE miko_work_id = 'mk:mb:46378' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_73/1738646064303wQu2v_JPEG/2619.jpg'
             WHERE miko_work_id = 'mk:mb:46378' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:55025', 160, 1135641, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5122', 'mk:mb:55025', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5122', 'url', 'https://www.webtoons.com/en/horror/moonchild/list?title_no=5122'))
         WHERE miko_work_id = 'mk:mb:55025' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:55025' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251122_152/1763784113900GAf3F_JPEG/4) Thumb_Poster_5122.jpg'
             WHERE miko_work_id = 'mk:mb:55025' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:80569', 161, 1109080, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2410', 'mk:mb:80569', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2410', 'url', 'https://www.webtoons.com/en/mystery/garden-club-detective-squad/list?title_no=2410'))
         WHERE miko_work_id = 'mk:mb:80569' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_248/17386460533487Ke4s_JPEG/2410.jpg'
             WHERE miko_work_id = 'mk:mb:80569' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:2776', 162, 1096292, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7927', 'mk:mb:2776', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7927', 'url', 'https://www.webtoons.com/en/romance/aiming-for-the-alimony/list?title_no=7927'))
         WHERE miko_work_id = 'mk:mb:2776' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260326_98/17744676667327IkEs_JPEG/0Thumb_Poster_7927.jpg'
             WHERE miko_work_id = 'mk:mb:2776' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:509006', 163, 1065355, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7317', 'mk:mb:509006', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7317', 'url', 'https://www.webtoons.com/en/comedy/nina-lives-alone/list?title_no=7317'))
         WHERE miko_work_id = 'mk:mb:509006' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_140/1738646159759RbCM6_JPEG/7317.jpg'
             WHERE miko_work_id = 'mk:mb:509006' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:528548', 164, 1040293, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1605', 'mk:mb:528548', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1605', 'url', 'https://www.webtoons.com/en/sports/plum/list?title_no=1605'))
         WHERE miko_work_id = 'mk:mb:528548' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_168/1738646011197Hi4by_JPEG/1605.jpg'
             WHERE miko_work_id = 'mk:mb:528548' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:385818', 166, 990494, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5116', 'mk:mb:385818', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5116', 'url', 'https://www.webtoons.com/en/thriller/silent-screams/list?title_no=5116'))
         WHERE miko_work_id = 'mk:mb:385818' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:385818' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251211_236/1765397684204X36qx_JPEG/Thumb_Poster_5116.jpg'
             WHERE miko_work_id = 'mk:mb:385818' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:3798', 167, 980936, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7458', 'mk:mb:3798', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7458', 'url', 'https://www.webtoons.com/en/historical/the-sichuan-tang-clans-entomologist/list?title_no=7458'))
         WHERE miko_work_id = 'mk:mb:3798' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250226_228/1740506722104GXh4m_JPEG/Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:3798' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:1558', 168, 975719, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6203', 'mk:mb:1558', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6203', 'url', 'https://www.webtoons.com/en/comedy/demon-prince-and-the-salaryman/list?title_no=6203'))
         WHERE miko_work_id = 'mk:mb:1558' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:1558' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_161/1738719533809CgoLu_JPEG/6203.jpg'
             WHERE miko_work_id = 'mk:mb:1558' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:54271', 169, 966803, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5169', 'mk:mb:54271', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5169', 'url', 'https://www.webtoons.com/en/sports/king-of-the-octagon/list?title_no=5169'))
         WHERE miko_work_id = 'mk:mb:54271' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_66/1738719557317KOhUm_JPEG/5169.jpg'
             WHERE miko_work_id = 'mk:mb:54271' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:77320', 171, 957483, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6667', 'mk:mb:77320', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6667', 'url', 'https://www.webtoons.com/en/thriller/the-gwichon-village-mystery/list?title_no=6667'))
         WHERE miko_work_id = 'mk:mb:77320' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:77320' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260418_127/1776478464071X2nuo_JPEG/0Thumb_Poster_6667.jpg'
             WHERE miko_work_id = 'mk:mb:77320' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:508002', 172, 937842, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7064', 'mk:mb:508002', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7064', 'url', 'https://www.webtoons.com/en/comedy/my-daddy-is-a-villain/list?title_no=7064'))
         WHERE miko_work_id = 'mk:mb:508002' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251204_228/1764786467635Tzykr_JPEG/Thumb_Poster_7064.jpg'
             WHERE miko_work_id = 'mk:mb:508002' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:79090', 174, 934405, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2968', 'mk:mb:79090', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2968', 'url', 'https://www.webtoons.com/en/slice-of-life/kidults/list?title_no=2968'))
         WHERE miko_work_id = 'mk:mb:79090' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_259/1738718537336lukht_PNG/2968.png'
             WHERE miko_work_id = 'mk:mb:79090' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:37320', 175, 908630, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3176', 'mk:mb:37320', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3176', 'url', 'https://www.webtoons.com/en/thriller/hell-is-other-people/list?title_no=3176'))
         WHERE miko_work_id = 'mk:mb:37320' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_296/17387187110561pKYz_JPEG/3176.jpg'
             WHERE miko_work_id = 'mk:mb:37320' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:33419', 176, 893884, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2733', 'mk:mb:33419', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2733', 'url', 'https://www.webtoons.com/en/super-hero/immortal-weakling/list?title_no=2733'))
         WHERE miko_work_id = 'mk:mb:33419' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251216_293/1765854803548o5HLb_JPEG/4) Thumb_Poster_2733.jpg'
             WHERE miko_work_id = 'mk:mb:33419' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:3800', 177, 882425, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7111', 'mk:mb:3800', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7111', 'url', 'https://www.webtoons.com/en/fantasy/my-avatars-path-to-greatness/list?title_no=7111'))
         WHERE miko_work_id = 'mk:mb:3800' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260721_268/17845639259880Wz6I_JPEG/4Thumb_Poster_7111.jpg'
             WHERE miko_work_id = 'mk:mb:3800' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:77595', 178, 873902, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6743', 'mk:mb:77595', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6743', 'url', 'https://www.webtoons.com/en/sports/skool-of-street/list?title_no=6743'))
         WHERE miko_work_id = 'mk:mb:77595' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260613_212/1781287538564qcyDg_JPEG/1Skool_of_Street_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:77595' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:4061', 179, 868095, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6532', 'mk:mb:4061', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6532', 'url', 'https://www.webtoons.com/en/historical/myst-might-mayhem/list?title_no=6532'))
         WHERE miko_work_id = 'mk:mb:4061' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:4061' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_32/1738719889862uW6Ec_JPEG/6532.jpg'
             WHERE miko_work_id = 'mk:mb:4061' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:111789', 180, 850968, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7500', 'mk:mb:111789', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7500', 'url', 'https://www.webtoons.com/en/drama/the-regressed-empresss-abduction-marriage/list?title_no=7500'))
         WHERE miko_work_id = 'mk:mb:111789' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250416_90/1744763803345pv6Ca_JPEG/Thumb_Poster_The Regressed Empress’s Abduction Marriage.jpg'
             WHERE miko_work_id = 'mk:mb:111789' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:4122', 181, 845626, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6742', 'mk:mb:4122', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6742', 'url', 'https://www.webtoons.com/en/historical/reincarnated-murim-lord/list?title_no=6742'))
         WHERE miko_work_id = 'mk:mb:4122' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251122_86/1763757543525Sz5lm_JPEG/Thumb_Poster_6742.jpg'
             WHERE miko_work_id = 'mk:mb:4122' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:3849', 182, 842022, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7695', 'mk:mb:3849', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7695', 'url', 'https://www.webtoons.com/en/fantasy/i-will-protect-my-tryant-dad/list?title_no=7695'))
         WHERE miko_work_id = 'mk:mb:3849' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260520_191/1779227302680CltkO_JPEG/6Thumb_Poster_7695.jpg'
             WHERE miko_work_id = 'mk:mb:3849' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:117181', 183, 826869, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5725', 'mk:mb:117181', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5725', 'url', 'https://www.webtoons.com/en/thriller/there-are-no-demons/list?title_no=5725'))
         WHERE miko_work_id = 'mk:mb:117181' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:117181' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250418_81/1744927336962tGfMB_JPEG/Thumb_Poster_7694.jpg'
             WHERE miko_work_id = 'mk:mb:117181' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:36048', 184, 825782, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1340', 'mk:mb:36048', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1340', 'url', 'https://www.webtoons.com/en/sf/moonyou/list?title_no=1340'))
         WHERE miko_work_id = 'mk:mb:36048' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_128/1738645894584NgaOl_JPEG/1340.jpg'
             WHERE miko_work_id = 'mk:mb:36048' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:114927', 185, 812653, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4287', 'mk:mb:114927', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4287', 'url', 'https://www.webtoons.com/en/slice-of-life/suhee0/list?title_no=4287'))
         WHERE miko_work_id = 'mk:mb:114927' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:114927' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_98/1738719272855ePxd1_JPEG/4287.jpg'
             WHERE miko_work_id = 'mk:mb:114927' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:363903', 187, 794672, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8998', 'mk:mb:363903', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8998', 'url', 'https://www.webtoons.com/en/romance/born-to-be-the-grand-duchess/list?title_no=8998'))
         WHERE miko_work_id = 'mk:mb:363903' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260321_289/1774055227993PwDRT_JPEG/0Born_to_be_the_Grand_Duchess_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:363903' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:362153', 188, 793019, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8177', 'mk:mb:362153', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8177', 'url', 'https://www.webtoons.com/en/fantasy/dungeon-crawler-carl/list?title_no=8177'))
         WHERE miko_work_id = 'mk:mb:362153' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:362153' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250724_97/1753303516771gNqB3_JPEG/DungeonCrawlerCarl_Launch_250721_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:362153' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:90916', 190, 730003, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3451', 'mk:mb:90916', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3451', 'url', 'https://www.webtoons.com/en/sports/infinity/list?title_no=3451'))
         WHERE miko_work_id = 'mk:mb:90916' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_37/1738718988415OA6qV_JPEG/3451.jpg'
             WHERE miko_work_id = 'mk:mb:90916' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:131546', 191, 724377, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2563', 'mk:mb:131546', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2563', 'url', 'https://www.webtoons.com/en/supernatural/this-magical-moment/list?title_no=2563'))
         WHERE miko_work_id = 'mk:mb:131546' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_159/173871810461310ack_JPEG/2563.jpg'
             WHERE miko_work_id = 'mk:mb:131546' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:42833', 192, 723885, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3185', 'mk:mb:42833', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3185', 'url', 'https://www.webtoons.com/en/supernatural/spirit-hunter/list?title_no=3185'))
         WHERE miko_work_id = 'mk:mb:42833' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:42833' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_64/1738718672960msKLM_JPEG/3185.jpg'
             WHERE miko_work_id = 'mk:mb:42833' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:535349', 193, 704338, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '633', 'mk:mb:535349', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '633', 'url', 'https://www.webtoons.com/en/super-hero/supersonic-girl/list?title_no=633'))
         WHERE miko_work_id = 'mk:mb:535349' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_52/17386458699916Adx3_JPEG/633.jpg'
             WHERE miko_work_id = 'mk:mb:535349' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:43944', 194, 699660, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2605', 'mk:mb:43944', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2605', 'url', 'https://www.webtoons.com/en/sf/if-ai-ruled-the-world/list?title_no=2605'))
         WHERE miko_work_id = 'mk:mb:43944' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_152/1738646062285sKTi6_JPEG/2605.jpg'
             WHERE miko_work_id = 'mk:mb:43944' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:77583', 196, 693357, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5733', 'mk:mb:77583', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5733', 'url', 'https://www.webtoons.com/en/mystery/my-sisters-private-life/list?title_no=5733'))
         WHERE miko_work_id = 'mk:mb:77583' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_167/1738719483949rnFBr_JPEG/5733.jpg'
             WHERE miko_work_id = 'mk:mb:77583' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:544257', 198, 685940, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '1947', 'mk:mb:544257', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '1947', 'url', 'https://www.webtoons.com/en/supernatural/magic-soda-pop/list?title_no=1947'))
         WHERE miko_work_id = 'mk:mb:544257' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:544257' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_23/1738646038134YqHTk_JPEG/1947.jpg'
             WHERE miko_work_id = 'mk:mb:544257' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:45569', 199, 685396, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2515', 'mk:mb:45569', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2515', 'url', 'https://www.webtoons.com/en/mystery/locker-opener/list?title_no=2515'))
         WHERE miko_work_id = 'mk:mb:45569' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_295/1738646056571YmN8M_JPEG/2515.jpg'
             WHERE miko_work_id = 'mk:mb:45569' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:80893', 200, 680920, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7408', 'mk:mb:80893', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7408', 'url', 'https://www.webtoons.com/en/action/chronicles-of-a-doomed-prodigy/list?title_no=7408'))
         WHERE miko_work_id = 'mk:mb:80893' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:80893' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260820_263/1787174311781uN2Xc_JPEG/9Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:80893' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:30792', 201, 680335, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '541', 'mk:mb:30792', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '541', 'url', 'https://www.webtoons.com/en/super-hero/xinker/list?title_no=541'))
         WHERE miko_work_id = 'mk:mb:30792' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_191/1738645865294yURKi_JPEG/541.jpg'
             WHERE miko_work_id = 'mk:mb:30792' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:5138', 202, 664854, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2404', 'mk:mb:5138', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2404', 'url', 'https://www.webtoons.com/en/sf/mosquito-wars/list?title_no=2404'))
         WHERE miko_work_id = 'mk:mb:5138' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:5138' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_265/1738646051831alWYC_JPEG/2404.jpg'
             WHERE miko_work_id = 'mk:mb:5138' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:2684', 203, 639012, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7634', 'mk:mb:2684', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7634', 'url', 'https://www.webtoons.com/en/slice-of-life/the-genius-wants-an-ordinary-life/list?title_no=7634'))
         WHERE miko_work_id = 'mk:mb:2684' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260714_169/1783972758217C4Miy_JPEG/2Thumb_Poster_7634.jpg'
             WHERE miko_work_id = 'mk:mb:2684' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:76220', 204, 634437, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3049', 'mk:mb:76220', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3049', 'url', 'https://www.webtoons.com/en/super-hero/samadhi/list?title_no=3049'))
         WHERE miko_work_id = 'mk:mb:76220' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_203/1738718699508f3YWp_PNG/3049.png'
             WHERE miko_work_id = 'mk:mb:76220' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:203280', 205, 630234, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8241', 'mk:mb:203280', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8241', 'url', 'https://www.webtoons.com/en/fantasy/eat-before-you-go/list?title_no=8241'))
         WHERE miko_work_id = 'mk:mb:203280' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250708_78/1751904481429yzjx8_JPEG/Thumb_Poster_8241.jpg'
             WHERE miko_work_id = 'mk:mb:203280' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:27442', 206, 621065, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '293', 'mk:mb:27442', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '293', 'url', 'https://www.webtoons.com/en/horror/dead-days/list?title_no=293'))
         WHERE miko_work_id = 'mk:mb:27442' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:27442' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_256/1738645848580UtLkW_JPEG/293.jpg'
             WHERE miko_work_id = 'mk:mb:27442' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:82418', 207, 615171, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7133', 'mk:mb:82418', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7133', 'url', 'https://www.webtoons.com/en/slice-of-life/little-hollow-cafe/list?title_no=7133'))
         WHERE miko_work_id = 'mk:mb:82418' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251203_48/1764702600976Mr7R2_JPEG/Thumb_Poster_7133.jpg'
             WHERE miko_work_id = 'mk:mb:82418' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:49945', 208, 597625, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3454', 'mk:mb:49945', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3454', 'url', 'https://www.webtoons.com/en/thriller/the-predator/list?title_no=3454'))
         WHERE miko_work_id = 'mk:mb:49945' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:49945' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_141/17387189939161bc5z_JPEG/3454.jpg'
             WHERE miko_work_id = 'mk:mb:49945' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:1555', 209, 596588, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6017', 'mk:mb:1555', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6017', 'url', 'https://www.webtoons.com/en/horror/bunny-girl-and-the-cult/list?title_no=6017'))
         WHERE miko_work_id = 'mk:mb:1555' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:1555' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_244/1738719522536pg6Gl_JPEG/6017.jpg'
             WHERE miko_work_id = 'mk:mb:1555' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:87373', 211, 576808, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8200', 'mk:mb:87373', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8200', 'url', 'https://www.webtoons.com/en/action/artifact-devouring-player/list?title_no=8200'))
         WHERE miko_work_id = 'mk:mb:87373' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260731_200/1785453930475Fws6y_JPEG/6Thumb_Poster_8200.jpg'
             WHERE miko_work_id = 'mk:mb:87373' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:350044', 212, 558524, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9297', 'mk:mb:350044', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9297', 'url', 'https://www.webtoons.com/en/romance/the-grand-dukes-bride-is-a-warrior-from-hell/list?title_no=9297'))
         WHERE miko_work_id = 'mk:mb:350044' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251223_93/176645588502000rjv_JPEG/4) Thumb_Poster_9297.jpg'
             WHERE miko_work_id = 'mk:mb:350044' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:2159', 213, 554260, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6668', 'mk:mb:2159', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6668', 'url', 'https://www.webtoons.com/en/super-hero/the-superhuman-era/list?title_no=6668'))
         WHERE miko_work_id = 'mk:mb:2159' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:2159' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_75/17387198709363ooBm_JPEG/6668.jpg'
             WHERE miko_work_id = 'mk:mb:2159' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:1016', 214, 547254, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6678', 'mk:mb:1016', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6678', 'url', 'https://www.webtoons.com/en/thriller/surviving-the-apocalypse/list?title_no=6678'))
         WHERE miko_work_id = 'mk:mb:1016' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:1016' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260911_252/1789080502835W1L0W_JPEG/8Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:1016' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:170781', 215, 545208, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8211', 'mk:mb:170781', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8211', 'url', 'https://www.webtoons.com/en/drama/proof-of-dignity/list?title_no=8211'))
         WHERE miko_work_id = 'mk:mb:170781' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250806_19/1754477648116Lbcvn_JPEG/Thumb_Poster_8211.jpg'
             WHERE miko_work_id = 'mk:mb:170781' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:81366', 217, 530404, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6887', 'mk:mb:81366', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6887', 'url', 'https://www.webtoons.com/en/thriller/my-girlfriend/list?title_no=6887'))
         WHERE miko_work_id = 'mk:mb:81366' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:81366' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_62/17386461207781ogrw_JPEG/6887.jpg'
             WHERE miko_work_id = 'mk:mb:81366' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:17075', 218, 527947, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '370', 'mk:mb:17075', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '370', 'url', 'https://www.webtoons.com/en/sf/duty-after-school/list?title_no=370'))
         WHERE miko_work_id = 'mk:mb:17075' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:17075' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_131/1738645852323N3OIV_JPEG/370.jpg'
             WHERE miko_work_id = 'mk:mb:17075' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:89393', 220, 523275, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3485', 'mk:mb:89393', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3485', 'url', 'https://www.webtoons.com/en/supernatural/floating-in-my-mind/list?title_no=3485'))
         WHERE miko_work_id = 'mk:mb:89393' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_21/1738718983417Hoj3H_JPEG/3485.jpg'
             WHERE miko_work_id = 'mk:mb:89393' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:1532', 221, 509254, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7913', 'mk:mb:1532', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7913', 'url', 'https://www.webtoons.com/en/historical/dr-kim-of-london/list?title_no=7913'))
         WHERE miko_work_id = 'mk:mb:1532' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250516_24/1747352911793pwfHr_JPEG/Thumb_Poster_7913.jpg'
             WHERE miko_work_id = 'mk:mb:1532' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:81319', 222, 508592, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4955', 'mk:mb:81319', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4955', 'url', 'https://www.webtoons.com/en/thriller/class-1-9/list?title_no=4955'))
         WHERE miko_work_id = 'mk:mb:81319' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:81319' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_107/1738719489642qeAds_JPEG/4955.jpg'
             WHERE miko_work_id = 'mk:mb:81319' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:90689', 223, 495261, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3202', 'mk:mb:90689', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3202', 'url', 'https://www.webtoons.com/en/mystery/anything-for-you/list?title_no=3202'))
         WHERE miko_work_id = 'mk:mb:90689' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250318_61/17422554552624Dxw5_JPEG/Thumb_Poster_3202.jpg'
             WHERE miko_work_id = 'mk:mb:90689' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:90215', 224, 491146, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7844', 'mk:mb:90215', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7844', 'url', 'https://www.webtoons.com/en/slice-of-life/mr-tigers-snacks/list?title_no=7844'))
         WHERE miko_work_id = 'mk:mb:90215' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250424_295/1745428331936QR9A1_JPEG/Thumb_Poster_7844.jpg'
             WHERE miko_work_id = 'mk:mb:90215' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:8832', 225, 485278, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6746', 'mk:mb:8832', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6746', 'url', 'https://www.webtoons.com/en/horror/the-last-passage/list?title_no=6746'))
         WHERE miko_work_id = 'mk:mb:8832' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:8832' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260522_6/1779401089607esWfk_JPEG/8Thumb_Poster_6746.jpg'
             WHERE miko_work_id = 'mk:mb:8832' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:124659', 226, 469337, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2955', 'mk:mb:124659', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2955', 'url', 'https://www.webtoons.com/en/sf/the-sound-of-another/list?title_no=2955'))
         WHERE miko_work_id = 'mk:mb:124659' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_277/1738718536763D5RHa_JPEG/2955.jpg'
             WHERE miko_work_id = 'mk:mb:124659' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:1609', 227, 468584, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7699', 'mk:mb:1609', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7699', 'url', 'https://www.webtoons.com/en/sports/im-not-a-soccer-genius/list?title_no=7699'))
         WHERE miko_work_id = 'mk:mb:1609' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250430_36/1745950240274B9Lrx_JPEG/Thumb_Poster_7699.jpg'
             WHERE miko_work_id = 'mk:mb:1609' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:532388', 228, 457865, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2247', 'mk:mb:532388', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2247', 'url', 'https://www.webtoons.com/en/horror/beetle-hands/list?title_no=2247'))
         WHERE miko_work_id = 'mk:mb:532388' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:532388' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_103/1738646046748ccweO_JPEG/2247.jpg'
             WHERE miko_work_id = 'mk:mb:532388' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:81173', 229, 452747, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3310', 'mk:mb:81173', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3310', 'url', 'https://www.webtoons.com/en/sports/taekwondo-kid/list?title_no=3310'))
         WHERE miko_work_id = 'mk:mb:81173' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_52/1738718706836jQJ6W_JPEG/3310.jpg'
             WHERE miko_work_id = 'mk:mb:81173' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:86739', 232, 432409, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8013', 'mk:mb:86739', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8013', 'url', 'https://www.webtoons.com/en/horror/44th-period-survival-class/list?title_no=8013'))
         WHERE miko_work_id = 'mk:mb:86739' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:86739' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260904_245/1788460506699yrNEC_JPEG/9Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:86739' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:362535', 233, 429499, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8946', 'mk:mb:362535', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8946', 'url', 'https://www.webtoons.com/en/heartwarming/the-s-class-hunters-peaceful-life-with-monsters/list?title_no=8946'))
         WHERE miko_work_id = 'mk:mb:362535' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251106_4/1762396131949qjg29_JPEG/Thumb_Poster_8946.jpg'
             WHERE miko_work_id = 'mk:mb:362535' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:201202', 234, 428238, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8557', 'mk:mb:201202', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8557', 'url', 'https://www.webtoons.com/en/drama/moralless-scandal/list?title_no=8557'))
         WHERE miko_work_id = 'mk:mb:201202' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250924_122/1758652011974vHGoy_JPEG/4) Thumb_Poster_8557.jpg'
             WHERE miko_work_id = 'mk:mb:201202' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:815', 235, 427894, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8808', 'mk:mb:815', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8808', 'url', 'https://www.webtoons.com/en/romance/dirty-deeds/list?title_no=8808'))
         WHERE miko_work_id = 'mk:mb:815' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:815' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250926_35/1758844731156jwSFV_JPEG/Thumb_Poster_8808.jpg'
             WHERE miko_work_id = 'mk:mb:815' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:3818', 236, 417909, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7951', 'mk:mb:3818', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7951', 'url', 'https://www.webtoons.com/en/supernatural/beastly-things/list?title_no=7951'))
         WHERE miko_work_id = 'mk:mb:3818' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250517_30/17474211900441WCYD_JPEG/Thumb_Poster_7951.jpg'
             WHERE miko_work_id = 'mk:mb:3818' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:567591', 238, 401813, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9702', 'mk:mb:567591', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9702', 'url', 'https://www.webtoons.com/en/action/too-many-heroes-for-the-demon-lord/list?title_no=9702'))
         WHERE miko_work_id = 'mk:mb:567591' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260325_156/1774389014856Ecx0t_JPEG/2Thumb_Poster_9702.jpg'
             WHERE miko_work_id = 'mk:mb:567591' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:125719', 239, 396583, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9591', 'mk:mb:125719', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9591', 'url', 'https://www.webtoons.com/en/romance/my-bias-is-obsessed-with-me/list?title_no=9591'))
         WHERE miko_work_id = 'mk:mb:125719' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260128_41/1769558785455J1MtK_JPEG/8Thumb_Poster_9591.jpg'
             WHERE miko_work_id = 'mk:mb:125719' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:24449', 240, 391350, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3789', 'mk:mb:24449', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3789', 'url', 'https://www.webtoons.com/en/slice-of-life/our-house/list?title_no=3789'))
         WHERE miko_work_id = 'mk:mb:24449' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_88/1738719315381eAeOX_JPEG/3789.jpg'
             WHERE miko_work_id = 'mk:mb:24449' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:365248', 241, 388040, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9175', 'mk:mb:365248', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9175', 'url', 'https://www.webtoons.com/en/fantasy/the-back-alley-mages-second-life/list?title_no=9175'))
         WHERE miko_work_id = 'mk:mb:365248' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251213_179/1765572888150ATUAJ_JPEG/4) Thumb_Poster_9175.jpg'
             WHERE miko_work_id = 'mk:mb:365248' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:90898', 242, 386183, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7644', 'mk:mb:90898', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7644', 'url', 'https://www.webtoons.com/en/slice-of-life/the-second-chance-convenience-store/list?title_no=7644'))
         WHERE miko_work_id = 'mk:mb:90898' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250228_33/1740700874380KxqXL_JPEG/Thumb_Poster_7644.jpg'
             WHERE miko_work_id = 'mk:mb:90898' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:522688', 244, 380508, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9508', 'mk:mb:522688', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9508', 'url', 'https://www.webtoons.com/en/fantasy/the-archmage-becomes-the-swordmasters-youngest-daughter/list?title_no=9508'))
         WHERE miko_work_id = 'mk:mb:522688' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260128_40/1769567311790vjAUn_JPEG/1Thumb_Poster_9508.jpg'
             WHERE miko_work_id = 'mk:mb:522688' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:7921', 245, 379042, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7861', 'mk:mb:7921', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7861', 'url', 'https://www.webtoons.com/en/horror/why-you-shouldnt-enter-a-haunted-house/list?title_no=7861'))
         WHERE miko_work_id = 'mk:mb:7921' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:7921' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251111_1/1762858409856IzbVy_JPEG/Thumb_Poster_7861.jpg'
             WHERE miko_work_id = 'mk:mb:7921' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:497273', 247, 349514, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4078', 'mk:mb:497273', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4078', 'url', 'https://www.webtoons.com/en/mystery/sunset-phoenix/list?title_no=4078'))
         WHERE miko_work_id = 'mk:mb:497273' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_264/1738719271047cBtvV_JPEG/4078.jpg'
             WHERE miko_work_id = 'mk:mb:497273' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:583099', 248, 342152, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10201', 'mk:mb:583099', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10201', 'url', 'https://www.webtoons.com/en/romance/the-scandal-maker-has-returned/list?title_no=10201'))
         WHERE miko_work_id = 'mk:mb:583099' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260525_231/1779685327457Uwl5Y_JPEG/2Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:583099' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:90001', 249, 335592, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3253', 'mk:mb:90001', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3253', 'url', 'https://www.webtoons.com/en/sf/harmonia/list?title_no=3253'))
         WHERE miko_work_id = 'mk:mb:90001' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_169/1738718686327UHtHD_PNG/3253.png'
             WHERE miko_work_id = 'mk:mb:90001' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:353632', 250, 324913, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9221', 'mk:mb:353632', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9221', 'url', 'https://www.webtoons.com/en/slice-of-life/itll-be-okay/list?title_no=9221'))
         WHERE miko_work_id = 'mk:mb:353632' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260117_90/1768600815618sxpO9_JPEG/4) Thumb_Poster_9221.jpg'
             WHERE miko_work_id = 'mk:mb:353632' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:89257', 251, 322413, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7404', 'mk:mb:89257', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7404', 'url', 'https://www.webtoons.com/en/sf/false-paradise/list?title_no=7404'))
         WHERE miko_work_id = 'mk:mb:89257' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_199/1738646171619s9jHK_JPEG/7404.jpg'
             WHERE miko_work_id = 'mk:mb:89257' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:384584', 252, 316169, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2962', 'mk:mb:384584', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2962', 'url', 'https://www.webtoons.com/en/mystery/betwixt/list?title_no=2962'))
         WHERE miko_work_id = 'mk:mb:384584' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250305_143/1741112019118wMULR_JPEG/2962.jpg'
             WHERE miko_work_id = 'mk:mb:384584' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:57085', 253, 314998, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4696', 'mk:mb:57085', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4696', 'url', 'https://www.webtoons.com/en/mystery/the-secrets-of-the-willson-family/list?title_no=4696'))
         WHERE miko_work_id = 'mk:mb:57085' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:57085' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_195/1738719279181s5LmE_JPEG/4696.jpg'
             WHERE miko_work_id = 'mk:mb:57085' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:530051', 254, 314684, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '622', 'mk:mb:530051', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '622', 'url', 'https://www.webtoons.com/en/tiptoon/cooking-comically/list?title_no=622'))
         WHERE miko_work_id = 'mk:mb:530051' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_9/1738645867417tvXUg_JPEG/622.jpg'
             WHERE miko_work_id = 'mk:mb:530051' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:53168', 255, 314522, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5479', 'mk:mb:53168', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5479', 'url', 'https://www.webtoons.com/en/horror/attention-residents/list?title_no=5479'))
         WHERE miko_work_id = 'mk:mb:53168' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:53168' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_269/1738719477315SMomX_JPEG/5479.jpg'
             WHERE miko_work_id = 'mk:mb:53168' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:130751', 256, 311183, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7864', 'mk:mb:130751', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7864', 'url', 'https://www.webtoons.com/en/slice-of-life/winter-breeze/list?title_no=7864'))
         WHERE miko_work_id = 'mk:mb:130751' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250513_257/1747091622871235Pr_JPEG/Thumb_Poster_7864.jpg'
             WHERE miko_work_id = 'mk:mb:130751' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:591839', 257, 300744, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7585', 'mk:mb:591839', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7585', 'url', 'https://www.webtoons.com/en/supernatural/words-left-unspoken/list?title_no=7585'))
         WHERE miko_work_id = 'mk:mb:591839' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260515_145/1778809533532mEoJk_JPEG/3Words_Left_Unspoken_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:591839' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:62534', 258, 299364, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5547', 'mk:mb:62534', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5547', 'url', 'https://www.webtoons.com/en/heartwarming/tata-the-cat/list?title_no=5547'))
         WHERE miko_work_id = 'mk:mb:62534' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_17/1738719553018fMeWx_JPEG/5547.jpg'
             WHERE miko_work_id = 'mk:mb:62534' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:47192', 259, 297755, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2403', 'mk:mb:47192', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2403', 'url', 'https://www.webtoons.com/en/sf/flowar/list?title_no=2403'))
         WHERE miko_work_id = 'mk:mb:47192' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:47192' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_256/1738646051762LxRab_JPEG/2403.jpg'
             WHERE miko_work_id = 'mk:mb:47192' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:81316', 262, 290418, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6137', 'mk:mb:81316', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6137', 'url', 'https://www.webtoons.com/en/thriller/memorial/list?title_no=6137'))
         WHERE miko_work_id = 'mk:mb:81316' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_19/1738719666664UgIAm_JPEG/6137.jpg'
             WHERE miko_work_id = 'mk:mb:81316' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:579689', 263, 278873, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10039', 'mk:mb:579689', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10039', 'url', 'https://www.webtoons.com/en/fantasy/the-shepherd-wizard/list?title_no=10039'))
         WHERE miko_work_id = 'mk:mb:579689' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:579689' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260430_37/17775071121412Swl3_JPEG/5Thumb_Poster_10039.jpg'
             WHERE miko_work_id = 'mk:mb:579689' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:45312', 264, 270115, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3236', 'mk:mb:45312', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3236', 'url', 'https://www.webtoons.com/en/slice-of-life/she-bites/list?title_no=3236'))
         WHERE miko_work_id = 'mk:mb:45312' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_141/1738718667663hqUK5_JPEG/3236.jpg'
             WHERE miko_work_id = 'mk:mb:45312' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:73596', 265, 268835, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7524', 'mk:mb:73596', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7524', 'url', 'https://www.webtoons.com/en/drama/momfluencer/list?title_no=7524'))
         WHERE miko_work_id = 'mk:mb:73596' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:73596' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250305_215/1741114060391lF1e1_PNG/Thumb_Poster_7524.png'
             WHERE miko_work_id = 'mk:mb:73596' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:356464', 267, 266447, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8945', 'mk:mb:356464', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8945', 'url', 'https://www.webtoons.com/en/drama/one-day-my-sister-died/list?title_no=8945'))
         WHERE miko_work_id = 'mk:mb:356464' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251114_128/1763083382668tRTNb_JPEG/Thumb_Poster_8945.jpg'
             WHERE miko_work_id = 'mk:mb:356464' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:135', 268, 264825, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9761', 'mk:mb:135', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9761', 'url', 'https://www.webtoons.com/en/fantasy/night-shift-in-another-world/list?title_no=9761'))
         WHERE miko_work_id = 'mk:mb:135' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260306_217/1772756701600ADTu6_JPEG/1Thumb_Poster_9761.jpg'
             WHERE miko_work_id = 'mk:mb:135' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:87266', 269, 260497, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8202', 'mk:mb:87266', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8202', 'url', 'https://www.webtoons.com/en/drama/ancient-animal-tales/list?title_no=8202'))
         WHERE miko_work_id = 'mk:mb:87266' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250708_224/1751928785733mlloj_JPEG/Thumb_Poster_8202.jpg'
             WHERE miko_work_id = 'mk:mb:87266' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:568308', 270, 251204, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9700', 'mk:mb:568308', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9700', 'url', 'https://www.webtoons.com/en/action/reincarnated-as-the-grand-dukes-third-son/list?title_no=9700'))
         WHERE miko_work_id = 'mk:mb:568308' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:568308' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260312_272/1773302421765B2MCn_JPEG/0Thumb_Poster_9700.jpg'
             WHERE miko_work_id = 'mk:mb:568308' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:114715', 271, 251155, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3350', 'mk:mb:114715', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3350', 'url', 'https://www.webtoons.com/en/horror/zombie-ship/list?title_no=3350'))
         WHERE miko_work_id = 'mk:mb:114715' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:114715' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_166/1738718974759dBDKT_JPEG/3350.jpg'
             WHERE miko_work_id = 'mk:mb:114715' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:90429', 272, 249576, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6883', 'mk:mb:90429', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6883', 'url', 'https://www.webtoons.com/en/heartwarming/humanoido/list?title_no=6883'))
         WHERE miko_work_id = 'mk:mb:90429' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_202/1738646120070dS8TL_JPEG/6883.jpg'
             WHERE miko_work_id = 'mk:mb:90429' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:39117', 273, 234033, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3235', 'mk:mb:39117', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3235', 'url', 'https://www.webtoons.com/en/supernatural/11-of-me/list?title_no=3235'))
         WHERE miko_work_id = 'mk:mb:39117' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_270/1738718669851Uv7TT_JPEG/3235.jpg'
             WHERE miko_work_id = 'mk:mb:39117' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:505619', 274, 231794, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6592', 'mk:mb:505619', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6592', 'url', 'https://www.webtoons.com/en/supernatural/spectral-bonds/list?title_no=6592'))
         WHERE miko_work_id = 'mk:mb:505619' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260808_124/1786123882638PNEnR_JPEG/1Thumb_Poster_6592.jpg'
             WHERE miko_work_id = 'mk:mb:505619' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:60939', 276, 220891, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8957', 'mk:mb:60939', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8957', 'url', 'https://www.webtoons.com/en/thriller/undercover-at-chaebol-high/list?title_no=8957'))
         WHERE miko_work_id = 'mk:mb:60939' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:60939' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251113_110/1762972343660Bzg31_JPEG/4) Thumb_Poster_8957.jpg'
             WHERE miko_work_id = 'mk:mb:60939' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:200845', 277, 210548, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5200', 'mk:mb:200845', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5200', 'url', 'https://www.webtoons.com/en/sports/the-tomato-can/list?title_no=5200'))
         WHERE miko_work_id = 'mk:mb:200845' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_154/1738719633025dPFtL_JPEG/5200.jpg'
             WHERE miko_work_id = 'mk:mb:200845' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:37147', 279, 204163, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7523', 'mk:mb:37147', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7523', 'url', 'https://www.webtoons.com/en/historical/the-forbidden-marriage/list?title_no=7523'))
         WHERE miko_work_id = 'mk:mb:37147' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250308_96/17413639568691Lrca_JPEG/Thumb_Poster_7532.jpg'
             WHERE miko_work_id = 'mk:mb:37147' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:133402', 280, 197075, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4084', 'mk:mb:133402', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4084', 'url', 'https://www.webtoons.com/en/horror/roach/list?title_no=4084'))
         WHERE miko_work_id = 'mk:mb:133402' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:133402' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_153/1738719280523i0c8E_JPEG/4084.jpg'
             WHERE miko_work_id = 'mk:mb:133402' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:88594', 281, 197018, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7405', 'mk:mb:88594', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7405', 'url', 'https://www.webtoons.com/en/sports/deadpoint/list?title_no=7405'))
         WHERE miko_work_id = 'mk:mb:88594' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260106_29/1767661523735XkwnC_JPEG/4) Thumb_Poster_7405.jpg'
             WHERE miko_work_id = 'mk:mb:88594' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:59001', 282, 193679, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5831', 'mk:mb:59001', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5831', 'url', 'https://www.webtoons.com/en/super-hero/hero-the-maximum/list?title_no=5831'))
         WHERE miko_work_id = 'mk:mb:59001' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_168/1738719663331m21sj_JPEG/5831.jpg'
             WHERE miko_work_id = 'mk:mb:59001' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:186720', 283, 193410, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8563', 'mk:mb:186720', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8563', 'url', 'https://www.webtoons.com/en/thriller/campfire-friends/list?title_no=8563'))
         WHERE miko_work_id = 'mk:mb:186720' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251016_205/1760548373371SSrl9_JPEG/4) Thumb_Poster_8563.jpg'
             WHERE miko_work_id = 'mk:mb:186720' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:54307', 284, 192898, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4211', 'mk:mb:54307', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4211', 'url', 'https://www.webtoons.com/en/horror/apocalypse-live/list?title_no=4211'))
         WHERE miko_work_id = 'mk:mb:54307' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:54307' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_130/1738719180845welqp_JPEG/4211.jpg'
             WHERE miko_work_id = 'mk:mb:54307' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:132556', 285, 191033, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7356', 'mk:mb:132556', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7356', 'url', 'https://www.webtoons.com/en/heartwarming/the-memory-shop/list?title_no=7356'))
         WHERE miko_work_id = 'mk:mb:132556' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_154/17386461611591wU30_JPEG/7356.jpg'
             WHERE miko_work_id = 'mk:mb:132556' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:72572', 287, 186957, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3665', 'mk:mb:72572', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3665', 'url', 'https://www.webtoons.com/en/tiptoon/sarah-im-sorry-welcome-to-our-galaxy/list?title_no=3665'))
         WHERE miko_work_id = 'mk:mb:72572' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_68/1738719002677yJiTs_JPEG/3665.jpg'
             WHERE miko_work_id = 'mk:mb:72572' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:570576', 289, 179630, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9841', 'mk:mb:570576', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9841', 'url', 'https://www.webtoons.com/en/action/the-sword-eating-swordmaster/list?title_no=9841'))
         WHERE miko_work_id = 'mk:mb:570576' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260331_133/1774895113749DddbB_JPEG/6Thumb_Poster_9841.jpg'
             WHERE miko_work_id = 'mk:mb:570576' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:87652', 291, 168407, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4493', 'mk:mb:87652', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4493', 'url', 'https://www.webtoons.com/en/sports/big-man-on-the-court/list?title_no=4493'))
         WHERE miko_work_id = 'mk:mb:87652' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:87652' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_121/1738719256099EvjV6_JPEG/4493.jpg'
             WHERE miko_work_id = 'mk:mb:87652' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:585766', 292, 164410, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9661', 'mk:mb:585766', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9661', 'url', 'https://www.webtoons.com/en/action/defiance-of-the-fall/list?title_no=9661'))
         WHERE miko_work_id = 'mk:mb:585766' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260327_239/1774551912731uGi2g_JPEG/9Thumb_Poster_9661.jpg'
             WHERE miko_work_id = 'mk:mb:585766' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:357709', 293, 164332, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8811', 'mk:mb:357709', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8811', 'url', 'https://www.webtoons.com/en/supernatural/shamoneyism/list?title_no=8811'))
         WHERE miko_work_id = 'mk:mb:357709' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:357709' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250930_117/1759197389945gF14n_JPEG/Thumb_Poster_8811.jpg'
             WHERE miko_work_id = 'mk:mb:357709' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:515628', 295, 163768, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9856', 'mk:mb:515628', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9856', 'url', 'https://www.webtoons.com/en/drama/assistant-manager-kim-hates-k-pop-idols/list?title_no=9856'))
         WHERE miko_work_id = 'mk:mb:515628' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260430_51/1777482574395nvexr_JPEG/4Thumb_Poster_9856.jpg'
             WHERE miko_work_id = 'mk:mb:515628' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:581542', 296, 161330, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10336', 'mk:mb:581542', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10336', 'url', 'https://www.webtoons.com/en/romance/i-only-need-our-child-your-grace/list?title_no=10336'))
         WHERE miko_work_id = 'mk:mb:581542' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:581542' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260615_184/17815033012754vhSx_JPEG/3Thumb_Poster-2_10336.jpg'
             WHERE miko_work_id = 'mk:mb:581542' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:583578', 297, 161069, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10255', 'mk:mb:583578', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10255', 'url', 'https://www.webtoons.com/en/heartwarming/sir-prestons-secret-governess/list?title_no=10255'))
         WHERE miko_work_id = 'mk:mb:583578' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260605_129/1780604187981Qx0Fk_JPEG/6Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:583578' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:567353', 298, 160616, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9884', 'mk:mb:567353', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9884', 'url', 'https://www.webtoons.com/en/fantasy/archmage-curriculum/list?title_no=9884'))
         WHERE miko_work_id = 'mk:mb:567353' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260421_10/1776707278376c6yxW_JPEG/4Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:567353' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:505620', 299, 156707, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6594', 'mk:mb:505620', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6594', 'url', 'https://www.webtoons.com/en/sf/miracle-simulator/list?title_no=6594'))
         WHERE miko_work_id = 'mk:mb:505620' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250715_161/1752521351044Uc8la_JPEG/Thumb_Poster_6594.jpg'
             WHERE miko_work_id = 'mk:mb:505620' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:579597', 300, 153771, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10037', 'mk:mb:579597', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10037', 'url', 'https://www.webtoons.com/en/action/resurrection-boy/list?title_no=10037'))
         WHERE miko_work_id = 'mk:mb:579597' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260522_189/1779394798141FIvQu_JPEG/8Thumb_Poster_10037.jpg'
             WHERE miko_work_id = 'mk:mb:579597' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:572364', 301, 151887, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9857', 'mk:mb:572364', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9857', 'url', 'https://www.webtoons.com/en/thriller/copycat/list?title_no=9857'))
         WHERE miko_work_id = 'mk:mb:572364' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:572364' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260411_226/1775862865829T9hMA_JPEG/0Thumb_Poster_9857.jpg'
             WHERE miko_work_id = 'mk:mb:572364' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:574985', 302, 151270, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7580', 'mk:mb:574985', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7580', 'url', 'https://www.webtoons.com/en/horror/house-pool/list?title_no=7580'))
         WHERE miko_work_id = 'mk:mb:574985' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:574985' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251028_127/1761586521882qHNVz_JPEG/Thumb_Poster_7580.jpg'
             WHERE miko_work_id = 'mk:mb:574985' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:98043', 305, 148452, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7858', 'mk:mb:98043', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7858', 'url', 'https://www.webtoons.com/en/sports/tiki-taka/list?title_no=7858'))
         WHERE miko_work_id = 'mk:mb:98043' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250509_294/17467295448921PSMy_JPEG/Thumb_Poster_7858.jpg'
             WHERE miko_work_id = 'mk:mb:98043' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:2515', 307, 146436, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8860', 'mk:mb:2515', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8860', 'url', 'https://www.webtoons.com/en/horror/love-thy-neighbor/list?title_no=8860'))
         WHERE miko_work_id = 'mk:mb:2515' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:2515' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251023_14/1761220632792rT8KW_JPEG/Thumb_Poster_8860.jpg'
             WHERE miko_work_id = 'mk:mb:2515' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:566308', 308, 146357, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9859', 'mk:mb:566308', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9859', 'url', 'https://www.webtoons.com/en/romance/my-meddler-in-love/list?title_no=9859'))
         WHERE miko_work_id = 'mk:mb:566308' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260414_278/1776145564804thWAb_JPEG/7Thumb_Poster_9859.jpg'
             WHERE miko_work_id = 'mk:mb:566308' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:485245', 309, 144880, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '2882', 'mk:mb:485245', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '2882', 'url', 'https://www.webtoons.com/en/mystery/do-you-remember/list?title_no=2882'))
         WHERE miko_work_id = 'mk:mb:485245' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_272/1738718525164MimTy_JPEG/2882.jpg'
             WHERE miko_work_id = 'mk:mb:485245' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:92648', 310, 144591, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7779', 'mk:mb:92648', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7779', 'url', 'https://www.webtoons.com/en/sf/no-kids-zone/list?title_no=7779'))
         WHERE miko_work_id = 'mk:mb:92648' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250422_19/1745313042268EsPLh_JPEG/Thumb_Poster_7779.jpg'
             WHERE miko_work_id = 'mk:mb:92648' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:402928', 311, 141699, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9719', 'mk:mb:402928', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9719', 'url', 'https://www.webtoons.com/en/action/the-reborn-prodigy-of-the-ducal-family/list?title_no=9719'))
         WHERE miko_work_id = 'mk:mb:402928' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260313_225/1773359050452chLll_JPEG/6Thumb_Poster_9719.jpg'
             WHERE miko_work_id = 'mk:mb:402928' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:69692', 314, 137577, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8003', 'mk:mb:69692', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8003', 'url', 'https://www.webtoons.com/en/comedy/legend-of-the-holy-sword/list?title_no=8003'))
         WHERE miko_work_id = 'mk:mb:69692' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260606_82/1780681313131ViOK9_JPEG/3Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:69692' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:186694', 316, 134263, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8421', 'mk:mb:186694', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8421', 'url', 'https://www.webtoons.com/en/thriller/the-end-has-come/list?title_no=8421'))
         WHERE miko_work_id = 'mk:mb:186694' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:186694' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250815_213/1755191637418y4TqN_JPEG/Thumb_Poster_8421.jpg'
             WHERE miko_work_id = 'mk:mb:186694' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:569875', 317, 131517, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9701', 'mk:mb:569875', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9701', 'url', 'https://www.webtoons.com/en/action/a-wimps-tower-strategy-guide/list?title_no=9701'))
         WHERE miko_work_id = 'mk:mb:569875' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:569875' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260317_205/1773690735946ggr82_JPEG/4Thumb_Poster_9701.jpg'
             WHERE miko_work_id = 'mk:mb:569875' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:92335', 318, 130992, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7926', 'mk:mb:92335', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7926', 'url', 'https://www.webtoons.com/en/comedy/its-my-life/list?title_no=7926'))
         WHERE miko_work_id = 'mk:mb:92335' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260805_190/1785873108275Gd6DQ_JPEG/4___________It_s_My_Life_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:92335' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:545202', 319, 127010, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '675', 'mk:mb:545202', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '675', 'url', 'https://www.webtoons.com/en/super-hero/inarime/list?title_no=675'))
         WHERE miko_work_id = 'mk:mb:545202' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_146/1738645873389uUVEw_JPEG/675.jpg'
             WHERE miko_work_id = 'mk:mb:545202' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:98366', 320, 126737, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8033', 'mk:mb:98366', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8033', 'url', 'https://www.webtoons.com/en/thriller/destined-killer/list?title_no=8033'))
         WHERE miko_work_id = 'mk:mb:98366' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:98366' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250612_45/1749662416851rO8DJ_JPEG/Thumb_Poster_8033.jpg'
             WHERE miko_work_id = 'mk:mb:98366' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:54273', 321, 125909, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3480', 'mk:mb:54273', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3480', 'url', 'https://www.webtoons.com/en/sf/r-x-rayer-hunter/list?title_no=3480'))
         WHERE miko_work_id = 'mk:mb:54273' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:54273' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_161/1738718998035D9Hrs_JPEG/3480.jpg'
             WHERE miko_work_id = 'mk:mb:54273' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:360319', 323, 122379, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8948', 'mk:mb:360319', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8948', 'url', 'https://www.webtoons.com/en/slice-of-life/hello-little-griffin/list?title_no=8948'))
         WHERE miko_work_id = 'mk:mb:360319' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251103_58/1762146456671U0wQ4_JPEG/Thumb_Poster_8948.jpg'
             WHERE miko_work_id = 'mk:mb:360319' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:43547', 325, 117574, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5718', 'mk:mb:43547', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5718', 'url', 'https://www.webtoons.com/en/sf/the-tide/list?title_no=5718'))
         WHERE miko_work_id = 'mk:mb:43547' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:43547' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_199/1738719499705ogmcX_JPEG/5718.jpg'
             WHERE miko_work_id = 'mk:mb:43547' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:370395', 327, 111282, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9300', 'mk:mb:370395', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9300', 'url', 'https://www.webtoons.com/en/drama/living-with-romeo/list?title_no=9300'))
         WHERE miko_work_id = 'mk:mb:370395' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260117_55/1768606677014DrgXC_JPEG/Thumb_Poster_9300.jpg'
             WHERE miko_work_id = 'mk:mb:370395' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:369100', 329, 104502, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9472', 'mk:mb:369100', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9472', 'url', 'https://www.webtoons.com/en/slice-of-life/all-you-need-is-perfection/list?title_no=9472'))
         WHERE miko_work_id = 'mk:mb:369100' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260113_8/1768282251886E2MDF_JPEG/4) Thumb_Poster_9472.jpg'
             WHERE miko_work_id = 'mk:mb:369100' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:356460', 330, 103467, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8955', 'mk:mb:356460', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8955', 'url', 'https://www.webtoons.com/en/mystery/the-murderer-targets-brunettes/list?title_no=8955'))
         WHERE miko_work_id = 'mk:mb:356460' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260606_56/1780691205747mXEIu_JPEG/6Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:356460' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:369833', 331, 103434, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10258', 'mk:mb:369833', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10258', 'url', 'https://www.webtoons.com/en/thriller/the-classmate/list?title_no=10258'))
         WHERE miko_work_id = 'mk:mb:369833' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:369833' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260613_38/1781289262062qMUNA_JPEG/2TheClassmate_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:369833' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:98200', 332, 101526, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6724', 'mk:mb:98200', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6724', 'url', 'https://www.webtoons.com/en/sports/overtime-elite/list?title_no=6724'))
         WHERE miko_work_id = 'mk:mb:98200' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_210/1738719890188t4tb7_JPEG/6724.jpg'
             WHERE miko_work_id = 'mk:mb:98200' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:6699', 333, 100238, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10928', 'mk:mb:6699', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10928', 'url', 'https://www.webtoons.com/en/romance/father-i-dont-want-this-marriage/list?title_no=10928'))
         WHERE miko_work_id = 'mk:mb:6699' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260826_17/1787709943688O0Op3_JPEG/4Thumb_Poster_10928.jpg'
             WHERE miko_work_id = 'mk:mb:6699' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:566858', 334, 98600, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9699', 'mk:mb:566858', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9699', 'url', 'https://www.webtoons.com/en/drama/my-wavering-husband/list?title_no=9699'))
         WHERE miko_work_id = 'mk:mb:566858' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:566858' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260313_187/1773391237830gjuEo_JPEG/3Thumb_Poster_9699.jpg'
             WHERE miko_work_id = 'mk:mb:566858' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:82104', 335, 94991, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7205', 'mk:mb:82104', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7205', 'url', 'https://www.webtoons.com/en/mystery/day-of-disaster/list?title_no=7205'))
         WHERE miko_work_id = 'mk:mb:82104' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:82104' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_33/1738646150882dcRhj_JPEG/7205.jpg'
             WHERE miko_work_id = 'mk:mb:82104' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:135169', 336, 90985, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4289', 'mk:mb:135169', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4289', 'url', 'https://www.webtoons.com/en/slice-of-life/with-tony/list?title_no=4289'))
         WHERE miko_work_id = 'mk:mb:135169' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_47/1738719219532Iy6sB_JPEG/4289.jpg'
             WHERE miko_work_id = 'mk:mb:135169' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:190222', 337, 88246, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8657', 'mk:mb:190222', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8657', 'url', 'https://www.webtoons.com/en/thriller/survive/list?title_no=8657'))
         WHERE miko_work_id = 'mk:mb:190222' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:190222' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250927_116/1758911745972Lr19t_PNG/Thumb_Poster_8657.png'
             WHERE miko_work_id = 'mk:mb:190222' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:586520', 338, 85700, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10750', 'mk:mb:586520', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10750', 'url', 'https://www.webtoons.com/en/fantasy/the-otherworldly-family-man/list?title_no=10750'))
         WHERE miko_work_id = 'mk:mb:586520' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260731_85/1785454526599HtbiA_JPEG/9Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:586520' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:200380', 339, 83800, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8698', 'mk:mb:200380', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8698', 'url', 'https://www.webtoons.com/en/sf/grand-finale/list?title_no=8698'))
         WHERE miko_work_id = 'mk:mb:200380' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251017_214/1760679848815rxlAN_JPEG/4) Thumb_Poster_8698.jpg'
             WHERE miko_work_id = 'mk:mb:200380' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:564947', 340, 83361, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9721', 'mk:mb:564947', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9721', 'url', 'https://www.webtoons.com/en/drama/daughter-of-a-superstar/list?title_no=9721'))
         WHERE miko_work_id = 'mk:mb:564947' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260311_214/1773167829169RlviI_JPEG/1Thumb_Poster_9721.jpg'
             WHERE miko_work_id = 'mk:mb:564947' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:357550', 341, 82669, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9137', 'mk:mb:357550', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9137', 'url', 'https://www.webtoons.com/en/mystery/the-villain-profiler/list?title_no=9137'))
         WHERE miko_work_id = 'mk:mb:357550' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:357550' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251204_45/17648067395697fopx_JPEG/Thumb_Poster_9137.jpg'
             WHERE miko_work_id = 'mk:mb:357550' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:388147', 342, 82560, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5804', 'mk:mb:388147', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5804', 'url', 'https://www.webtoons.com/en/slice-of-life/i-hate-that-i-love-you/list?title_no=5804'))
         WHERE miko_work_id = 'mk:mb:388147' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251209_27/1765239823382bpzIC_JPEG/4) Thumb_Poster_5804.jpg'
             WHERE miko_work_id = 'mk:mb:388147' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:10298', 343, 81088, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '4012', 'mk:mb:10298', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '4012', 'url', 'https://www.webtoons.com/en/sports/be-still-my-heart/list?title_no=4012'))
         WHERE miko_work_id = 'mk:mb:10298' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:10298' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_187/17387191869358abWz_JPEG/4012.jpg'
             WHERE miko_work_id = 'mk:mb:10298' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:45516', 344, 79987, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '7820', 'mk:mb:45516', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '7820', 'url', 'https://www.webtoons.com/en/graphic-novel/cyberpunk-2077/list?title_no=7820'))
         WHERE miko_work_id = 'mk:mb:45516' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:45516' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250808_158/1754592666281RLnSF_JPEG/Cyberpunk2077_ThumbPoster.jpg'
             WHERE miko_work_id = 'mk:mb:45516' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:189764', 346, 77871, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9067', 'mk:mb:189764', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9067', 'url', 'https://www.webtoons.com/en/sports/swimmer-boy/list?title_no=9067'))
         WHERE miko_work_id = 'mk:mb:189764' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251209_103/17652484919102WOqY_PNG/4) Thumb_Poster_9067.png'
             WHERE miko_work_id = 'mk:mb:189764' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:361750', 347, 74567, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8858', 'mk:mb:361750', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8858', 'url', 'https://www.webtoons.com/en/thriller/social-coin-your-price/list?title_no=8858'))
         WHERE miko_work_id = 'mk:mb:361750' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:361750' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251016_216/17605488397863hDcc_JPEG/Thumb_Poster_8858.jpg'
             WHERE miko_work_id = 'mk:mb:361750' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:595795', 349, 73356, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10565', 'mk:mb:595795', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10565', 'url', 'https://www.webtoons.com/en/action/rise-of-the-limitless-necromancer/list?title_no=10565'))
         WHERE miko_work_id = 'mk:mb:595795' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260630_43/1782786985336qScAR_JPEG/6Rise_of_the_Limitless_Necromancer_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:595795' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:536361', 350, 73318, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '3916', 'mk:mb:536361', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '3916', 'url', 'https://www.webtoons.com/en/super-hero/eternals-500-year-war/list?title_no=3916'))
         WHERE miko_work_id = 'mk:mb:536361' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_132/1738719202573gRToy_JPEG/3916.jpg'
             WHERE miko_work_id = 'mk:mb:536361' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:585558', 351, 73204, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10260', 'mk:mb:585558', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10260', 'url', 'https://www.webtoons.com/en/comedy/purely-delinquent/list?title_no=10260'))
         WHERE miko_work_id = 'mk:mb:585558' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260625_94/1782332089458VumLn_JPEG/8Thumb_Poster_10260.jpg'
             WHERE miko_work_id = 'mk:mb:585558' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:568690', 352, 72496, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10196', 'mk:mb:568690', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10196', 'url', 'https://www.webtoons.com/en/drama/self-immolation/list?title_no=10196'))
         WHERE miko_work_id = 'mk:mb:568690' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:568690' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260511_217/1778495976301tevuB_JPEG/2Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:568690' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:587143', 359, 63909, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10737', 'mk:mb:587143', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10737', 'url', 'https://www.webtoons.com/en/fantasy/reborn-as-a-hatchling/list?title_no=10737'))
         WHERE miko_work_id = 'mk:mb:587143' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260821_154/1787256095443jmEs6_JPEG/7Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:587143' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:167755', 362, 60561, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '5123', 'mk:mb:167755', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '5123', 'url', 'https://www.webtoons.com/en/supernatural/vampire-syndrome/list?title_no=5123'))
         WHERE miko_work_id = 'mk:mb:167755' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:167755' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250205_60/1738719509076BBFu1_JPEG/5123.jpg'
             WHERE miko_work_id = 'mk:mb:167755' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:365247', 364, 58848, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9263', 'mk:mb:365247', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9263', 'url', 'https://www.webtoons.com/en/sports/the-genius-midfielder-with-the-killer-pass/list?title_no=9263'))
         WHERE miko_work_id = 'mk:mb:365247' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251217_288/1765952060706Cj8N5_JPEG/4) Thumb_Poster_9263 .jpg'
             WHERE miko_work_id = 'mk:mb:365247' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:569569', 365, 55633, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10282', 'mk:mb:569569', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10282', 'url', 'https://www.webtoons.com/en/thriller/cute-war/list?title_no=10282'))
         WHERE miko_work_id = 'mk:mb:569569' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260611_36/1781138093789rnPzh_JPEG/0Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:569569' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:514279', 367, 55086, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10168', 'mk:mb:514279', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10168', 'url', 'https://www.webtoons.com/en/drama/her-name-of-go/list?title_no=10168'))
         WHERE miko_work_id = 'mk:mb:514279' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:514279' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260501_10/1777570531667p2iht_JPEG/84__Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:514279' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:77393', 368, 54608, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '6853', 'mk:mb:77393', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '6853', 'url', 'https://www.webtoons.com/en/horror/banhyang-the-remote-island/list?title_no=6853'))
         WHERE miko_work_id = 'mk:mb:77393' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:77393' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250204_88/1738646120008n9En6_JPEG/6853.jpg'
             WHERE miko_work_id = 'mk:mb:77393' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:566413', 369, 54166, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9704', 'mk:mb:566413', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9704', 'url', 'https://www.webtoons.com/en/thriller/pretty-collection/list?title_no=9704'))
         WHERE miko_work_id = 'mk:mb:566413' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:566413' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260326_298/1774467216224zJSjN_JPEG/7Thumb_Poster_9704.jpg'
             WHERE miko_work_id = 'mk:mb:566413' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:480682', 370, 53719, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10169', 'mk:mb:480682', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10169', 'url', 'https://www.webtoons.com/en/drama/good-work-today/list?title_no=10169'))
         WHERE miko_work_id = 'mk:mb:480682' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260519_118/1779128643646V3JNx_JPEG/7Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:480682' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:585767', 371, 53362, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10480', 'mk:mb:585767', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10480', 'url', 'https://www.webtoons.com/en/action/the-margraves-10th-class-outcast/list?title_no=10480'))
         WHERE miko_work_id = 'mk:mb:585767' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:585767' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260724_68/17848407300866qlWu_JPEG/4Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:585767' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:1420', 373, 50789, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9720', 'mk:mb:1420', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9720', 'url', 'https://www.webtoons.com/en/drama/the-aura-of-a-genius-actor/list?title_no=9720'))
         WHERE miko_work_id = 'mk:mb:1420' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260324_189/1774333644712AnkYf_JPEG/4Thumb_Poster_9720.jpg'
             WHERE miko_work_id = 'mk:mb:1420' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:599851', 374, 47869, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10924', 'mk:mb:599851', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10924', 'url', 'https://www.webtoons.com/en/action/rebirth-monarch-of-the-dead/list?title_no=10924'))
         WHERE miko_work_id = 'mk:mb:599851' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260811_276/178639395830585pSj_JPEG/0Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:599851' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:523375', 379, 40995, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10281', 'mk:mb:523375', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10281', 'url', 'https://www.webtoons.com/en/thriller/spoiler/list?title_no=10281'))
         WHERE miko_work_id = 'mk:mb:523375' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260618_140/17817411158863fYDF_JPEG/0Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:523375' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:41906', 381, 39904, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8963', 'mk:mb:41906', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8963', 'url', 'https://www.webtoons.com/en/mystery/maid-in-heaven/list?title_no=8963'))
         WHERE miko_work_id = 'mk:mb:41906' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251025_188/1761323365581xAzMg_JPEG/4) Thumb_Poster_8963.jpg'
             WHERE miko_work_id = 'mk:mb:41906' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:368958', 382, 35592, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9244', 'mk:mb:368958', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9244', 'url', 'https://www.webtoons.com/en/supernatural/vincent-the-great/list?title_no=9244'))
         WHERE miko_work_id = 'mk:mb:368958' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251220_281/17661741794980e8c3_JPEG/4) Thumb_Poster_9244.jpg'
             WHERE miko_work_id = 'mk:mb:368958' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:204170', 384, 30897, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8705', 'mk:mb:204170', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8705', 'url', 'https://www.webtoons.com/en/sports/slow-dive/list?title_no=8705'))
         WHERE miko_work_id = 'mk:mb:204170' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251025_284/1761328651851jet1J_JPEG/Thumb_Poster_8705.jpg'
             WHERE miko_work_id = 'mk:mb:204170' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:581751', 387, 28496, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10252', 'mk:mb:581751', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10252', 'url', 'https://www.webtoons.com/en/drama/the-god-of-improv/list?title_no=10252'))
         WHERE miko_work_id = 'mk:mb:581751' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260612_27/1781217104523TBG5I_JPEG/7Thumb_Poster_10252.jpg'
             WHERE miko_work_id = 'mk:mb:581751' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:86719', 388, 27582, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8457', 'mk:mb:86719', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8457', 'url', 'https://www.webtoons.com/en/mystery/one-of-three/list?title_no=8457'))
         WHERE miko_work_id = 'mk:mb:86719' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250829_20/1756410214777VNlbh_JPEG/Thumb_Poster_8457.jpg'
             WHERE miko_work_id = 'mk:mb:86719' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:89466', 392, 25054, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8741', 'mk:mb:89466', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8741', 'url', 'https://www.webtoons.com/en/horror/shape-of-the-nightmare/list?title_no=8741'))
         WHERE miko_work_id = 'mk:mb:89466' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:89466' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251023_200/1761191212162PKjyj_JPEG/4) Thumb_Poster_8741.jpg'
             WHERE miko_work_id = 'mk:mb:89466' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:587014', 395, 22535, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10741', 'mk:mb:587014', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10741', 'url', 'https://www.webtoons.com/en/comedy/a-siblings-pov/list?title_no=10741'))
         WHERE miko_work_id = 'mk:mb:587014' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260822_177/1787356912362V9LVo_JPEG/4Thumb_Poster_10741.jpg'
             WHERE miko_work_id = 'mk:mb:587014' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:89542', 396, 22217, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9136', 'mk:mb:89542', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9136', 'url', 'https://www.webtoons.com/en/sf/youth-blossoms/list?title_no=9136'))
         WHERE miko_work_id = 'mk:mb:89542' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20251202_299/17646556456404SfUg_PNG/4)-Thumb_Poster_9136.png'
             WHERE miko_work_id = 'mk:mb:89542' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:109310', 398, 20588, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10488', 'mk:mb:109310', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10488', 'url', 'https://www.webtoons.com/en/horror/platfrom-zero/list?title_no=10488'))
         WHERE miko_work_id = 'mk:mb:109310' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:109310' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260701_252/1782859833915eLMYA_JPEG/3Thumb_Poster_10488.jpg'
             WHERE miko_work_id = 'mk:mb:109310' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:590378', 399, 20437, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '11212', 'mk:mb:590378', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '11212', 'url', 'https://www.webtoons.com/en/drama/trapped-together/list?title_no=11212'))
         WHERE miko_work_id = 'mk:mb:590378' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260905_90/1788563945870bDHdL_JPEG/3Trapped_Together_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:590378' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:589534', 401, 19837, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '11013', 'mk:mb:589534', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '11013', 'url', 'https://www.webtoons.com/en/action/green-skin/list?title_no=11013'))
         WHERE miko_work_id = 'mk:mb:589534' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:589534' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260919_124/1789751966060zwmYx_JPEG/1Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:589534' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:577011', 402, 19758, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10250', 'mk:mb:577011', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10250', 'url', 'https://www.webtoons.com/en/sf/the-journey-of-ice-and-steel/list?title_no=10250'))
         WHERE miko_work_id = 'mk:mb:577011' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260605_247/1780624950339mzjsc_JPEG/6Thumb_Poster_10250.jpg'
             WHERE miko_work_id = 'mk:mb:577011' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:364680', 405, 17172, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10245', 'mk:mb:364680', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10245', 'url', 'https://www.webtoons.com/en/heartwarming/sol-in-wonderland/list?title_no=10245'))
         WHERE miko_work_id = 'mk:mb:364680' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260528_213/1779934796780G1e8Q_JPEG/0SolinWonderland_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:364680' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:589183', 410, 15632, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10755', 'mk:mb:589183', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10755', 'url', 'https://www.webtoons.com/en/thriller/exhuma-blind-faith/list?title_no=10755'))
         WHERE miko_work_id = 'mk:mb:589183' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:589183' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260815_127/1786730198114NV652_JPEG/4Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:589183' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:359620', 413, 14487, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9864', 'mk:mb:359620', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9864', 'url', 'https://www.webtoons.com/en/comedy/a-warriors-daily-log/list?title_no=9864'))
         WHERE miko_work_id = 'mk:mb:359620' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260422_29/1776810102767o0I0j_JPEG/9Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:359620' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:570575', 415, 13350, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10283', 'mk:mb:570575', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10283', 'url', 'https://www.webtoons.com/en/thriller/the-symbiote-circuit/list?title_no=10283'))
         WHERE miko_work_id = 'mk:mb:570575' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260605_209/17806067537965IRvr_JPEG/1Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:570575' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:369283', 416, 12926, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10485', 'mk:mb:369283', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10485', 'url', 'https://www.webtoons.com/en/horror/hell-city/list?title_no=10485'))
         WHERE miko_work_id = 'mk:mb:369283' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260703_169/1783014074712WsNQE_JPEG/6Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:369283' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:590499', 418, 12321, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '11010', 'mk:mb:590499', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '11010', 'url', 'https://www.webtoons.com/en/drama/revenge-with-my-new-husband/list?title_no=11010'))
         WHERE miko_work_id = 'mk:mb:590499' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260905_101/1788548391925B7kEH_JPEG/9Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:590499' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:23551', 419, 11887, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '8646', 'mk:mb:23551', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '8646', 'url', 'https://www.webtoons.com/en/graphic-novel/apple-black/list?title_no=8646'))
         WHERE miko_work_id = 'mk:mb:23551' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20250905_227/1757014291993vaNMA_JPEG/AppleBlack_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:23551' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:398900', 420, 11370, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9717', 'mk:mb:398900', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9717', 'url', 'https://www.webtoons.com/en/sports/ankle-breaker/list?title_no=9717'))
         WHERE miko_work_id = 'mk:mb:398900' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260728_180/1785188721553vXV99_JPEG/5Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:398900' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:86384', 421, 9799, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9372', 'mk:mb:86384', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9372', 'url', 'https://www.webtoons.com/en/graphic-novel/gunhild/list?title_no=9372'))
         WHERE miko_work_id = 'mk:mb:86384' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260206_66/1770315551944PBvYk_JPEG/44__Thumb_Poster_9372.jpg'
             WHERE miko_work_id = 'mk:mb:86384' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:582026', 422, 9016, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10745', 'mk:mb:582026', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10745', 'url', 'https://www.webtoons.com/en/sports/dancer-on-the-court/list?title_no=10745'))
         WHERE miko_work_id = 'mk:mb:582026' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260801_37/1785514323229BKNmD_JPEG/4DanceronTheCourt_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:582026' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:364853', 425, 8069, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10749', 'mk:mb:364853', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10749', 'url', 'https://www.webtoons.com/en/drama/my-wife-came-to-her-funeral/list?title_no=10749'))
         WHERE miko_work_id = 'mk:mb:364853' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:364853' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260807_62/1786071598880cbLpb_JPEG/3My_Wife_Came_to_Her_Funeral_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:364853' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:563153', 426, 7791, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10751', 'mk:mb:563153', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10751', 'url', 'https://www.webtoons.com/en/sports/the-aces-are-obsessed-with-me/list?title_no=10751'))
         WHERE miko_work_id = 'mk:mb:563153' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260827_42/1787798847581BhJj1_JPEG/9All_the_Aces_Are_Obsessed_With_Me_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:563153' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:588612', 428, 6810, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '9869', 'mk:mb:588612', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '9869', 'url', 'https://www.webtoons.com/en/sf/aidie/list?title_no=9869'))
         WHERE miko_work_id = 'mk:mb:588612' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260422_120/1776810495579cVWl2_JPEG/0Thumb_Poster_9869.jpg'
             WHERE miko_work_id = 'mk:mb:588612' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:521201', 430, 4521, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10486', 'mk:mb:521201', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10486', 'url', 'https://www.webtoons.com/en/sf/a-fallen-star/list?title_no=10486'))
         WHERE miko_work_id = 'mk:mb:521201' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260718_29/1784327326652WUbr9_JPEG/5Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:521201' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:1655', 432, 4002, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '11067', 'mk:mb:1655', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '11067', 'url', 'https://www.webtoons.com/en/supernatural/demon-devourer/list?title_no=11067'))
         WHERE miko_work_id = 'mk:mb:1655' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = 'mk:mb:1655' AND nsfw = 0;
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260829_134/1787971635944QWuEl_JPEG/9Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:1655' AND cover_url IS NULL;
REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES ('webtoon_popular', 'mk:mb:601412', 433, 3631, 1790988419);
INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', '10883', 'mk:mb:601412', 1.0, 'webtoon_chart', 1790988419);
UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', '10883', 'url', 'https://www.webtoons.com/en/sports/back-to-first-base/list?title_no=10883'))
         WHERE miko_work_id = 'mk:mb:601412' AND provider_links NOT LIKE '%"webtoons"%';
UPDATE works_cache SET cover_url = 'https://webtoon-phinf.pstatic.net/20260827_289/1787787481925wKs7T_JPEG/410883_Thumb_Poster.jpg'
             WHERE miko_work_id = 'mk:mb:601412' AND cover_url IS NULL;
