package com.taoke.miquaner.serv;

import com.taoke.miquaner.data.ETbkItem;
import com.taoke.miquaner.data.EUser;
import com.taoke.miquaner.view.AliMaMaSubmit;
import com.taoke.miquaner.view.ShareSubmit;

import java.util.List;
import java.util.Map;

public interface ITbkServ {

    Object setAliMaMa(AliMaMaSubmit aliMaMaSubmit);

    Object getAliMaMa();

    Object getCouponByCid(String cid, Long pageNo, EUser user, boolean isSuper);

    Object getShareLink(ShareSubmit shareSubmit);

    Object getFavoriteList(Long pageNo);

    Object getFavoriteItems(Long favoriteId, Long pageNo, EUser user, boolean isSuper);

    Object search(EUser user, String keyword, Boolean isSuper);

    Object hints(String keyword);

    Map<Long, ETbkItem> loadSimpleItem(List<Long> id);

    Object getJuItems(EUser user, String keyword);

    void refreshKeyWord(String keyword);

    Object getTopSearchWords();

}
