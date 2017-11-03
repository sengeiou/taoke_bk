package com.taoke.miquaner.ctrl;

import com.taoke.miquaner.serv.IAdminServ;
import com.taoke.miquaner.util.Result;
import com.taoke.miquaner.view.SuperUserSubmit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminCtrl {

    @Autowired
    private IAdminServ adminServ;

    @RequestMapping("/admin/ping")
    public Object adminPing() {
        return Result.success("admin ping success");
    }

    @RequestMapping(value = "/admin/super/set", method = RequestMethod.POST)
    public Object setSuperUser(SuperUserSubmit superUserSubmit) {
        return this.adminServ.setSuperUser(superUserSubmit);
    }

}
