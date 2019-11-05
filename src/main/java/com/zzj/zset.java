package com.zzj;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.ZParams;

import java.util.*;

public class zset {
    private static final int ARTICLES_PER_PAGE = 5;

    public static void main(String[] args) {
        new zset().run();
    }

    public void run() {
        Jedis conn = new Jedis("localhost");
        conn.select(10);

        String articleId = postArticle(
                conn, "username2", "A title2", "http://www.google.com");
        System.out.println("We posted a new article with id: " + articleId);

//        String articleId = "1";
        articleVote(conn, "other_user", "article:" + articleId);

        List<Map<String,String>> articles = getArticles(conn, 1);
        printArticles(articles);

    }

    public String postArticle(Jedis conn, String user, String title, String link) {
        //1、生成一个新的文章ID
        String articleId = String.valueOf(conn.incr("article:"));     //String.valueOf(int i) : 将 int 变量 i 转换成字符串

        String voted = "voted:" + articleId;
        //2、添加到记录文章已投用户名单中，
        conn.sadd(voted, user);
        //3、设置一周为过期时间
        long now = System.currentTimeMillis() / 1000;
        String article = "article:" + articleId;
        //4、创建一个HashMap容器。
        HashMap<String, String> articleData = new HashMap<String, String>();
        articleData.put("title", title);
        articleData.put("link", link);
        articleData.put("user", user);
        articleData.put("now", String.valueOf(now));
        articleData.put("oppose", "0");
        articleData.put("votes", "1");
        //5、将文章信息存储到一个散列里面。
        //HMSET key field value [field value ...]
        //同时将多个 field-value (域-值)对设置到哈希表 key 中。
        //此命令会覆盖哈希表中已存在的域。
        conn.hmset(article, articleData);
        //6、将文章添加到更具发布时间排序的有序集合。
        conn.zadd("time:", now, article);
        conn.zadd("score:", 1, article);
        return articleId;
    }

    public void articleVote(Jedis conn, String user, String article) {
        String articleId = article.substring(article.indexOf(':') + 1);
        if (conn.sadd("voted:" + articleId, user) == 1) {
            conn.hincrBy(article, "votes", 1);
            conn.zincrby("score:", 1, article);//更新
        }
    }

    public List<Map<String,String>> getArticles(Jedis conn, int page) {
        //调用下面重载的方法
        return getArticles(conn, page, "score:");
    }

    public List<Map<String, String>> getArticles(Jedis conn, int page,String order) {
        //1、设置获取文章的起始索引和结束索引。
        int start = (page - 1) * ARTICLES_PER_PAGE;
        int end = start + ARTICLES_PER_PAGE - 1;
         //2、获取多个文章ID,
        Set<String> ids = conn.zrevrange(order, start, end);
        List<Map<String, String>> articles = new ArrayList<Map<String, String>>();
        for (String id : ids) {
            //3、根据文章ID获取文章的详细信息
            Map<String, String> articleData = conn.hgetAll(id);
            articleData.put("id", id);
            //4、添加到ArrayList容器中
            articles.add(articleData);
        }

        return articles;
    }

        private void printArticles(List<Map<String, String>> articles) {
        for (Map<String, String> article : articles) {
            System.out.println("  id: " + article.get("id"));
            for (Map.Entry<String, String> entry : article.entrySet()) {
                if (entry.getKey().equals("id")) {
                    continue;
                }
                System.out.println("    " + entry.getKey() + ": " + entry.getValue());
            }
        }
    }



}